package com.kizuna.notificationdelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.notification.transport.EmailTransport;
import com.kizuna.notificationdelivery.application.DeliveryAttempts;
import com.kizuna.notificationdelivery.application.DeliveryAudit;
import com.kizuna.notificationdelivery.application.DeliveryAuthorization;
import com.kizuna.notificationdelivery.application.DeliveryContact;
import com.kizuna.notificationdelivery.application.DeliveryDispatch;
import com.kizuna.notificationdelivery.application.NotificationActors;
import com.kizuna.notificationdelivery.application.NotificationDeliveryTask;
import com.kizuna.notificationdelivery.application.NotificationService;
import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import com.kizuna.notificationdelivery.domain.DeliveryAttemptRepository;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryStatus;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import com.kizuna.order.contact.BusinessContactDecision;
import com.kizuna.order.contact.OrderBusinessContact;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.task.execution.TaskContext;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;

/** 通知の各口が、引数（または送信事象）で受けた店舗だけを repository へ渡すことの単体テスト。 */
class NotificationApplicationTest {
  private static final Long STORE_ID = 7L;
  private static final AuditActor STAFF = new AuditActor(1L, "STAFF", "担当者");
  private static final AuditActor SERVICE = new AuditActor(5L, "SERVICE", "通知SERVICE");
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC);
  private final NotificationDeliveryRepository deliveries =
      mock(NotificationDeliveryRepository.class);
  private final DeliveryAttemptRepository attempts = mock(DeliveryAttemptRepository.class);
  private final NotificationActors actors = mock(NotificationActors.class);
  private final DeliveryContact contacts = mock(DeliveryContact.class);
  private final DeliveryAudit audit = mock(DeliveryAudit.class);
  private final NotificationService service =
      new NotificationService(
          deliveries, attempts, actors, contacts, audit, mock(EmailTransport.class), clock);

  @BeforeEach
  void setup() {
    when(actors.require(eq(STORE_ID), eq("actor"), any())).thenReturn(STAFF);
    when(contacts.decide(eq(STORE_ID), any()))
        .thenReturn(
            new OrderBusinessContact.Decision(
                "permitted@example.invalid", BusinessContactDecision.ALLOWED));
  }

  private static DeliveryContent content() {
    return new DeliveryContent(
        DeliveryContent.SourceType.ORDER,
        "123",
        "EMAIL",
        "BUSINESS",
        "件名",
        "本文",
        OffsetDateTime.parse("2026-10-07T09:00:00+09:00"),
        "key");
  }

  private static NotificationDelivery row() {
    var row = NotificationDelivery.draft(STORE_ID, content());
    row.setId("101");
    return row;
  }

  @Test
  void createLooksUpTheKeyAndDraftsTheRowInTheGivenStore() {
    when(deliveries.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    assertThat(service.create(STORE_ID, "actor", content()).created()).isTrue();
    verify(deliveries).findByStoreIdAndDedupeKey(STORE_ID, "key");
    var saved = ArgumentCaptor.forClass(NotificationDelivery.class);
    verify(deliveries).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStoreId()).isEqualTo(STORE_ID);
  }

  @Test
  void readsListsAndLocksOnlyWithinTheGivenStore() {
    var row = row();
    when(deliveries.findByIdAndStoreId("101", STORE_ID)).thenReturn(Optional.of(row));
    when(deliveries.findByIdAndStoreIdForUpdate("101", STORE_ID)).thenReturn(Optional.of(row));
    assertThat(service.get(STORE_ID, "actor", "101").id()).isEqualTo("101");
    assertThat(service.queue(STORE_ID, "actor", "101", null, "確認済み", false).status())
        .isEqualTo("QUEUED");
    service.history(STORE_ID, "actor", "101", null, 10);
    verify(attempts)
        .findByStoreIdAndDeliveryIdAndIdLessThanOrderByIdDesc(STORE_ID, "101", "~", Limit.of(11));
    service.list(STORE_ID, "actor", null, 10);
    verify(deliveries).findByStoreIdAndIdLessThanOrderByIdDesc(STORE_ID, "~", Limit.of(11));
  }

  @Test
  void anotherStoresDeliveryIsNotFound() {
    var other = STORE_ID + 1;
    when(actors.require(eq(other), eq("actor"), any())).thenReturn(STAFF);
    when(deliveries.findByIdAndStoreId("101", STORE_ID)).thenReturn(Optional.of(row()));
    assertThatThrownBy(() -> service.get(other, "actor", "101"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.queue(other, "actor", "101", null, "確認済み", false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void sweepTakesOnlyTheTaskStoreAndCopiesItIntoAttemptAndEvent() {
    var identities = mock(ServiceExecutionIdentityService.class);
    var events = mock(ApplicationEventPublisher.class);
    var task = new NotificationDeliveryTask(deliveries, attempts, identities, audit, events, clock);
    when(identities.requireService(5L, PermissionCode.NOTIFICATION_DELIVER, STORE_ID))
        .thenReturn(SERVICE);
    var row = row();
    row.queue(null, "確認済み", false);
    when(deliveries.findByStoreIdAndStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
            STORE_ID, DeliveryStatus.QUEUED, OffsetDateTime.now(clock), Limit.of(100)))
        .thenReturn(List.of(row));
    when(attempts.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              DeliveryAttempt attempt = call.getArgument(0);
              attempt.setId("201");
              return attempt;
            });
    assertThat(task.execute(new TaskContext(3L, 5L, STORE_ID, null, null))).isEqualTo(1);
    var attempt = ArgumentCaptor.forClass(DeliveryAttempt.class);
    verify(attempts).saveAndFlush(attempt.capture());
    assertThat(attempt.getValue().getStoreId()).isEqualTo(STORE_ID);
    var event = ArgumentCaptor.forClass(DeliveryDispatch.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().storeId()).isEqualTo(STORE_ID);
  }

  @Test
  void claimLocksTheEventStoresRows() {
    var authorization = mock(DeliveryAuthorization.class);
    var store = new StoreContext();
    var claims =
        new DeliveryAttempts(deliveries, attempts, authorization, contacts, audit, store, clock);
    var row = row();
    row.queue(null, "確認済み", false);
    row.dispatch();
    var attempt = DeliveryAttempt.dispatch(row, 3L, 5L, "通知SERVICE", OffsetDateTime.now(clock));
    attempt.setId("201");
    when(deliveries.findByIdAndStoreIdForUpdate("101", STORE_ID)).thenReturn(Optional.of(row));
    when(attempts.findByIdAndStoreId("201", STORE_ID)).thenReturn(Optional.of(attempt));
    when(authorization.require(5L, STORE_ID)).thenReturn(SERVICE);
    store.setStoreId(STORE_ID);
    try {
      assertThat(claims.claim(new DeliveryDispatch("101", "201", 5L, STORE_ID, 3L))).isTrue();
    } finally {
      store.clear();
    }
    assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.SENDING);
  }
}
