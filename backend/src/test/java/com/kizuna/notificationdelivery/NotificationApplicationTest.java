package com.kizuna.notificationdelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.notification.transport.ContactTransport;
import com.kizuna.notificationdelivery.application.ContactDecision;
import com.kizuna.notificationdelivery.application.DeliveryAttempts;
import com.kizuna.notificationdelivery.application.DeliveryAudit;
import com.kizuna.notificationdelivery.application.DeliveryAuthorization;
import com.kizuna.notificationdelivery.application.DeliveryContact;
import com.kizuna.notificationdelivery.application.DeliveryDispatch;
import com.kizuna.notificationdelivery.application.DeliveryRecipient;
import com.kizuna.notificationdelivery.application.DeliveryTransports;
import com.kizuna.notificationdelivery.application.NotificationActors;
import com.kizuna.notificationdelivery.application.NotificationDeliveryTask;
import com.kizuna.notificationdelivery.application.NotificationService;
import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import com.kizuna.notificationdelivery.domain.DeliveryAttemptRepository;
import com.kizuna.notificationdelivery.domain.DeliveryChannel;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.notificationdelivery.domain.DeliveryStatus;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.notificationdelivery.domain.NotificationDeliveryRepository;
import com.kizuna.shared.exception.NotFoundException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** 職員の口は引数の店舗だけを repository へ渡し、送信の定期処理は店舗を指定せずに全店舗の到来分を扱うことの単体テスト。 */
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
      new NotificationService(deliveries, attempts, actors, contacts, audit, transports(), clock);

  private static DeliveryTransports transports() {
    var email = mock(ContactTransport.class);
    when(email.type()).thenReturn(ContactType.EMAIL);
    return new DeliveryTransports(List.of(email));
  }

  @BeforeEach
  void setup() {
    when(actors.require(eq(STORE_ID), eq("actor"), any())).thenReturn(STAFF);
    when(contacts.resolve(eq(STORE_ID), any()))
        .thenReturn(new DeliveryRecipient("permitted@example.invalid", ContactDecision.ALLOWED));
  }

  private static DeliveryContent content() {
    return new DeliveryContent(
        DeliverySourceType.ORDER,
        "123",
        DeliveryChannel.EMAIL,
        DeliveryPurpose.BUSINESS,
        "件名",
        "本文",
        OffsetDateTime.parse("2026-10-07T09:00:00+09:00"),
        "key");
  }

  private static NotificationDelivery row() {
    var row = NotificationDelivery.draft(STORE_ID, content());
    ReflectionTestUtils.setField(row, "id", "101");
    return row;
  }

  @Test
  void createLooksUpTheKeyAndDraftsTheRowInTheGivenStore() {
    when(deliveries.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    assertThat(service.create(STORE_ID, "actor", content()).created()).isTrue();
    verify(deliveries).findByAttributedStoreIdAndDedupeKey(STORE_ID, "key");
    var saved = ArgumentCaptor.forClass(NotificationDelivery.class);
    verify(deliveries).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getAttributedStoreId()).isEqualTo(STORE_ID);
  }

  @Test
  void readsListsAndLocksOnlyWithinTheGivenStore() {
    var row = row();
    when(deliveries.findByIdAndAttributedStoreId("101", STORE_ID)).thenReturn(Optional.of(row));
    when(deliveries.findByIdAndAttributedStoreIdForUpdate("101", STORE_ID))
        .thenReturn(Optional.of(row));
    assertThat(service.get(STORE_ID, "actor", "101").row().getId()).isEqualTo("101");
    assertThat(service.queue(STORE_ID, "actor", "101", null, "確認済み", false).row().getStatus())
        .isEqualTo(DeliveryStatus.QUEUED);
    var pageable = PageRequest.of(0, 10);
    when(attempts.findByDeliveryId("101", pageable)).thenReturn(new PageImpl<>(List.of()));
    when(deliveries.findByAttributedStoreId(STORE_ID, pageable))
        .thenReturn(new PageImpl<>(List.of()));
    service.history(STORE_ID, "actor", "101", pageable);
    verify(attempts).findByDeliveryId("101", pageable);
    service.list(STORE_ID, "actor", pageable);
    verify(deliveries).findByAttributedStoreId(STORE_ID, pageable);
  }

  @Test
  void anotherStoresDeliveryIsNotFound() {
    var other = STORE_ID + 1;
    when(actors.require(eq(other), eq("actor"), any())).thenReturn(STAFF);
    when(deliveries.findByIdAndAttributedStoreId("101", STORE_ID)).thenReturn(Optional.of(row()));
    assertThatThrownBy(() -> service.get(other, "actor", "101"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.queue(other, "actor", "101", null, "確認済み", false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void sweepIsGroupWideAndTakesDueRowsOfEveryStore() {
    var identities = mock(ServiceExecutionIdentityService.class);
    var events = mock(ApplicationEventPublisher.class);
    var task = new NotificationDeliveryTask(deliveries, attempts, identities, audit, events, clock);
    when(identities.requireService(5L, PermissionCode.NOTIFICATION_DELIVER)).thenReturn(SERVICE);
    var row = row();
    row.queue(null, "確認済み", false);
    when(deliveries.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
            DeliveryStatus.QUEUED, OffsetDateTime.now(clock), Limit.of(100)))
        .thenReturn(List.of(row));
    when(attempts.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              DeliveryAttempt attempt = call.getArgument(0);
              ReflectionTestUtils.setField(attempt, "id", "201");
              return attempt;
            });
    assertThat(task.execute(new TaskContext(3L, 5L, null, null))).isEqualTo(1);
    verify(identities).requireService(5L, PermissionCode.TASK_EXECUTE);
    var event = ArgumentCaptor.forClass(DeliveryDispatch.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue()).isEqualTo(new DeliveryDispatch("101", "201", 5L, 3L));
  }

  @Test
  void claimLocksTheDispatchedRowByIdAndResolvesItsAttributedStore() {
    var authorization = mock(DeliveryAuthorization.class);
    var claims = new DeliveryAttempts(deliveries, attempts, authorization, contacts, audit, clock);
    var row = row();
    row.queue(null, "確認済み", false);
    row.dispatch();
    var attempt = DeliveryAttempt.dispatch(row, 3L, 5L, "通知SERVICE", OffsetDateTime.now(clock));
    ReflectionTestUtils.setField(attempt, "id", "201");
    when(deliveries.findByIdForUpdate("101")).thenReturn(Optional.of(row));
    when(attempts.findById("201")).thenReturn(Optional.of(attempt));
    when(authorization.require(5L)).thenReturn(SERVICE);
    assertThat(claims.claim(new DeliveryDispatch("101", "201", 5L, 3L))).isTrue();
    assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.SENDING);
    verify(contacts).resolve(STORE_ID, row.content());
  }
}
