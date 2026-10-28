package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.application.PointLedgerService.PointRollbackHistory;
import com.kizuna.point.application.PointLedgerService.PointRollbackPreview;
import com.kizuna.point.application.PointLedgerService.PointRollbackResult;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/** 受注帰属の救済 4 入口が、実 BusinessAudit / AuditWriter（保存先だけ差し替え）へ最小の記録を残すこと。 */
class OrderRescueAuditTest {
  private static final String ORDER_ID = "o1";
  private static final long STORE_ID = 1L;
  private static final String ACTOR_EMAIL = "manager@kizuna.test";
  private static final long ACTOR_ID = 42L;
  private static final long MEMBER_ID = 7L;
  private static final long ATTRIBUTION_ID = 501L;
  private static final String REASON = "別人の来店を取り違えたため";
  private static final String KEY = "idem-1";
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-14T10:00:00+09:00");

  private final OrderRepository orders = mock(OrderRepository.class);
  private final OrderAttributionRepository attributions = mock(OrderAttributionRepository.class);
  private final OrderReceiptTokenRepository tokens = mock(OrderReceiptTokenRepository.class);
  private final ReceiptTokenGenerator generator = mock(ReceiptTokenGenerator.class);
  private final PointLedgerService ledger = mock(PointLedgerService.class);
  private final UserRepository users = mock(UserRepository.class);
  private final List<AuditEvent> events = new ArrayList<>();
  private OrderAttributionService attributionService;
  private OrderAttributionCorrectionService correctionService;
  private OrderPointRollbackService rollbackService;
  private Order order;

  @BeforeEach
  void prepare() {
    User actor =
        User.builder()
            .email(ACTOR_EMAIL)
            .password("encoded")
            .displayName("店長 花子")
            .enabled(true)
            .userType(UserType.STAFF)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(STORE_ID))
            .roleIds(Set.of(1L))
            .build();
    actor.setId(ACTOR_ID);
    when(users.findByEmail(ACTOR_EMAIL)).thenReturn(Optional.of(actor));
    when(users.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
    var repository = mock(AuditEventRepository.class);
    when(repository.saveAndFlush(any(AuditEvent.class)))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              events.add(event);
              return event;
            });
    var audit = new BusinessAudit(users, new AuditWriter(repository, Clock.systemUTC()));
    var actors = new ActorIdentityService(users);
    attributionService =
        new OrderAttributionService(orders, attributions, tokens, generator, actors, audit);
    correctionService =
        new OrderAttributionCorrectionService(orders, attributions, ledger, actors, audit);
    rollbackService =
        new OrderPointRollbackService(
            orders, attributionService, attributions, tokens, ledger, actors, audit);

    order =
        Order.builder()
            .businessDate(LocalDate.parse("2026-08-10"))
            .status(OrderStatus.COMPLETED)
            .autoGrantPoints(50)
            .build();
    order.setId(ORDER_ID);
    order.setStoreId(STORE_ID);
    ReflectionTestUtils.setField(order, "version", 4L);
    when(orders.findByIdAndStoreId(ORDER_ID, STORE_ID)).thenReturn(Optional.of(order));
    when(orders.findByIdAndStoreIdForUpdate(ORDER_ID, STORE_ID)).thenReturn(Optional.of(order));
  }

  @Test
  @DisplayName("帰属の無効化は前後の状態と実 version だけを記録し、理由の本文は写さないこと")
  void invalidationRecordsStateAndVersionWithoutTheReason() {
    OrderAttribution active = attribution(false);
    givenAttributions(active);
    when(attributions.saveAndFlush(active))
        .thenAnswer(
            call -> {
              ReflectionTestUtils.setField(active, "version", 3L);
              return active;
            });

    attributionService.invalidate(STORE_ID, ORDER_ID, ATTRIBUTION_ID, REASON, ACTOR_EMAIL);

    AuditEvent event = onlyEvent();
    assertThat(event.getAction()).isEqualTo("ORDER_ATTRIBUTION_INVALIDATED");
    assertThat(event.getTargetType()).isEqualTo("ORDER_ATTRIBUTION");
    assertThat(event.getTargetId()).isEqualTo("501");
    assertThat(event.getSourceType()).isNull();
    assertThat(event.getActorId()).isEqualTo(ACTOR_ID);
    assertThat(event.getStoreId()).isEqualTo(STORE_ID);
    assertThat(event.getBeforeValues())
        .containsEntry("status", "ACTIVE")
        .containsEntry("version", "2");
    assertThat(event.getAfterValues())
        .containsEntry("status", "INVALIDATED")
        .containsEntry("version", "3")
        .containsEntry("redacted_fields_changed", "invalidated_reason");
    assertThat(everyValue(event)).doesNotContain(REASON);
  }

  @Test
  @DisplayName("画面を開いたまま帰属が入れ替わった無効化は 409 で、成功監査を作らないこと")
  void staleInvalidationLeavesNoAudit() {
    givenAttributions(attribution(false));

    assertThatThrownBy(
            () -> attributionService.invalidate(STORE_ID, ORDER_ID, 999L, REASON, ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class);

    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName("伝票の再発行は新トークン行を指し、生値と digest を写さず旧トークンの失効件数を示すこと")
  void reissueRecordsTheNewTokenRowWithoutTheSecret() {
    givenAttributions(attribution(true));
    OrderReceiptToken previous = OrderReceiptToken.issueFor(ORDER_ID, "old-digest", 30, NOW);
    when(tokens.findByOrderIdForUpdate(ORDER_ID)).thenReturn(List.of(previous));
    when(generator.generate())
        .thenReturn(new ReceiptTokenGenerator.GeneratedToken("raw-secret", "new-digest"));
    when(tokens.save(any(OrderReceiptToken.class)))
        .thenAnswer(
            call -> {
              OrderReceiptToken saved = call.getArgument(0);
              saved.setId(88L);
              return saved;
            });

    attributionService.reissueReceiptToken(STORE_ID, ORDER_ID, ACTOR_EMAIL);

    AuditEvent event = onlyEvent();
    assertThat(event.getAction()).isEqualTo("ORDER_RECEIPT_TOKEN_REISSUED");
    assertThat(event.getTargetType()).isEqualTo("ORDER");
    assertThat(event.getTargetId()).isEqualTo(ORDER_ID);
    assertThat(event.getSourceType()).isEqualTo("ORDER_RECEIPT_TOKEN");
    assertThat(event.getSourceId()).isEqualTo("88");
    assertThat(event.getBeforeValues()).containsEntry("active_tokens", "1");
    assertThat(event.getAfterValues())
        .containsEntry("active_tokens", "1")
        .containsEntry("revoked_tokens", "1")
        .containsEntry("token_status", "ISSUED");
    assertThat(everyValue(event)).doesNotContain("raw-secret", "new-digest", "old-digest");
  }

  @Test
  @DisplayName("巻き戻し済みの受注への再発行は拒否され、監査を作らないこと")
  void refusedReissueLeavesNoAudit() {
    when(ledger.isRolledBack(ORDER_ID)).thenReturn(true);

    assertThatThrownBy(
            () ->
                rollbackService.reissueReceiptTokenUnlessRolledBack(
                    STORE_ID, ORDER_ID, ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class);

    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName("帰属の訂正は今回の台帳仕訳を指し、訂正前後の累計と差分だけを記録すること")
  void correctionPointsAtTheNewLedgerEntry() {
    givenAttributions(attribution(true));
    givenLedger(40L);
    when(ledger.correctForAttribution(
            MEMBER_ID, STORE_ID, -30, REASON, ACTOR_ID, KEY, ATTRIBUTION_ID))
        .thenReturn(Optional.of(900L));

    correctionService.correct(STORE_ID, ORDER_ID, command(30), ACTOR_EMAIL);

    AuditEvent event = onlyEvent();
    assertThat(event.getAction()).isEqualTo("ORDER_ATTRIBUTION_CORRECTED");
    assertThat(event.getTargetType()).isEqualTo("ORDER_ATTRIBUTION");
    assertThat(event.getTargetId()).isEqualTo("501");
    assertThat(event.getSourceType()).isEqualTo("POINT_LEDGER_ENTRY");
    assertThat(event.getSourceId()).isEqualTo("900");
    assertThat(event.getBeforeValues())
        .containsEntry("corrected_points", "40")
        .containsEntry("member_id", "7");
    assertThat(event.getAfterValues()).containsEntry("corrected_points", "70");
    assertThat(everyValue(event)).doesNotContain(REASON, KEY);
  }

  @Test
  @DisplayName("台帳が再送として何も書かなかった訂正は、監査も足さないこと")
  void replayedCorrectionIsNotAuditedTwice() {
    givenAttributions(attribution(true));
    givenLedger(0L);
    when(ledger.correctForAttribution(
            MEMBER_ID, STORE_ID, -30, REASON, ACTOR_ID, KEY, ATTRIBUTION_ID))
        .thenReturn(Optional.empty());

    correctionService.correct(STORE_ID, ORDER_ID, command(30), ACTOR_EMAIL);
    correctionService.replayCorrect(STORE_ID, ORDER_ID, command(30), ACTOR_EMAIL);

    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName("上限を超える訂正は拒否され、監査を作らないこと")
  void overLimitCorrectionLeavesNoAudit() {
    givenAttributions(attribution(true));
    givenLedger(80L);

    assertThatThrownBy(
            () -> correctionService.correct(STORE_ID, ORDER_ID, command(30), ACTOR_EMAIL))
        .isInstanceOf(ServiceException.class);

    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName("ポイント巻き戻しは操作記録を指し、取消・復元ポイントと前後の受注状態を記録すること")
  void rollbackRecordsTheOperationRowAndMovedPoints() {
    givenAttributions(attribution(false));
    when(ledger.isRolledBack(ORDER_ID)).thenReturn(false);
    when(ledger.previewRollbackForOrder(ORDER_ID))
        .thenReturn(new PointRollbackPreview(false, 0, 0));
    when(ledger.rollbackForOrder(ORDER_ID, REASON, ACTOR_ID, 0))
        .thenReturn(new PointRollbackResult(120, 0));
    when(ledger.rollbackHistory(ORDER_ID))
        .thenReturn(
            Optional.of(new PointRollbackHistory("77", REASON, ACTOR_ID, NOW, 120, 0, 0, 0)));

    rollbackService.rollback(
        STORE_ID, ORDER_ID, new OrderPointRollbackCommand(0, 0, REASON), ACTOR_EMAIL);

    AuditEvent event = onlyEvent();
    assertThat(event.getAction()).isEqualTo("ORDER_POINTS_ROLLED_BACK");
    assertThat(event.getTargetType()).isEqualTo("ORDER");
    assertThat(event.getTargetId()).isEqualTo(ORDER_ID);
    assertThat(event.getSourceType()).isEqualTo("POINT_ROLLBACK");
    assertThat(event.getSourceId()).isEqualTo("77");
    assertThat(event.getBeforeValues())
        .containsEntry("rolled_back", "false")
        .containsEntry("attributed", "true");
    assertThat(event.getAfterValues())
        .containsEntry("rolled_back", "true")
        .containsEntry("cancelled_points", "120")
        .containsEntry("restored_points", "0");
    assertThat(everyValue(event)).doesNotContain(REASON);
  }

  @Test
  @DisplayName("二度目の巻き戻しは 409 で、監査を作らないこと")
  void secondRollbackLeavesNoAudit() {
    when(ledger.isRolledBack(ORDER_ID)).thenReturn(true);

    assertThatThrownBy(
            () ->
                rollbackService.rollback(
                    STORE_ID, ORDER_ID, new OrderPointRollbackCommand(0, 0, REASON), ACTOR_EMAIL))
        .isInstanceOf(ConflictException.class);

    assertThat(events).isEmpty();
  }

  private void givenAttributions(OrderAttribution... rows) {
    when(attributions.findByStoreIdAndOrderIdOrderByIdDesc(STORE_ID, ORDER_ID))
        .thenReturn(List.of(rows));
    when(attributions.findByStoreIdAndOrderIdOrderByIdDesc(
            eq(STORE_ID), eq(ORDER_ID), any(Limit.class)))
        .thenReturn(List.of(rows[0]));
  }

  private void givenLedger(long alreadyCorrected) {
    when(ledger.isIdempotencyKeyCommitted(anyString())).thenReturn(false);
    when(ledger.grantedPointsByOrder(MEMBER_ID, List.of(ORDER_ID)))
        .thenReturn(Map.of(ORDER_ID, 100L));
    when(ledger.correctedPointsFor(anyCollection(), anyString())).thenReturn(alreadyCorrected);
  }

  private static OrderAttribution attribution(boolean invalidated) {
    OrderAttribution row = OrderAttribution.onCompletion(ORDER_ID, MEMBER_ID, "123456789012", NOW);
    row.setId(ATTRIBUTION_ID);
    ReflectionTestUtils.setField(row, "version", 2L);
    if (invalidated) row.invalidate(REASON, ACTOR_ID, NOW);
    return row;
  }

  private static OrderAttributionCorrectionCommand command(int points) {
    return new OrderAttributionCorrectionCommand(ATTRIBUTION_ID, points, REASON, KEY);
  }

  private AuditEvent onlyEvent() {
    assertThat(events).hasSize(1);
    return events.get(0);
  }

  private static String everyValue(AuditEvent event) {
    return event.getBeforeValues().toString() + event.getAfterValues();
  }
}
