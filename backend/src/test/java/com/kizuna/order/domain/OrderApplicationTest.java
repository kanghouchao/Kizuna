package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderApplicationTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-08-20");
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-08-20T18:30:00+09:00");

  private OrderApplication pendingFor(LocalDate businessDate) {
    return OrderApplication.builder()
        .status(OrderApplicationStatus.PENDING)
        .businessDate(businessDate)
        .build();
  }

  @Test
  @DisplayName("未処理の申請を確定でき、受注 id・実行者・時刻が回写されること")
  void confirmWith_fromPending_recordsOrderActorAndTime() {
    OrderApplication application = pendingFor(TODAY);

    application.confirmWith("order-1", 7L, AT, TODAY);

    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.CONFIRMED);
    assertThat(application.getOrderId()).isEqualTo("order-1");
    assertThat(application.getProcessedBy()).isEqualTo(7L);
    assertThat(application.getProcessedAt()).isEqualTo(AT);
  }

  @Test
  @DisplayName("未処理の申請を理由付きで謝絶でき、理由・実行者・時刻が残ること")
  void decline_fromPending_recordsReasonActorAndTime() {
    OrderApplication application = pendingFor(TODAY);

    application.decline("満席のためお受けできません", 7L, AT, TODAY);

    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.DECLINED);
    assertThat(application.getDeclinedReason()).isEqualTo("満席のためお受けできません");
    assertThat(application.getProcessedBy()).isEqualTo(7L);
    assertThat(application.getProcessedAt()).isEqualTo(AT);
  }

  @Test
  @DisplayName("理由を欠く謝絶は撥ねられ、状態が動かないこと")
  void decline_requiresReason() {
    OrderApplication application = pendingFor(TODAY);
    assertThatThrownBy(() -> application.decline(null, 7L, AT, TODAY))
        .isInstanceOf(InvalidOrderApplicationOperationException.class);
    assertThatThrownBy(() -> application.decline("  ", 7L, AT, TODAY))
        .isInstanceOf(InvalidOrderApplicationOperationException.class);
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    assertThat(application.getDeclinedReason()).isNull();
  }

  @Test
  @DisplayName("本人が未処理の申請を取り下げられること")
  void withdraw_fromPending() {
    OrderApplication application = pendingFor(TODAY);

    application.withdraw(42L, AT);

    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.WITHDRAWN);
    assertThat(application.getProcessedBy()).isEqualTo(42L);
    assertThat(application.getProcessedAt()).isEqualTo(AT);
  }

  @Test
  @DisplayName("終端（確定・謝絶・取り下げ）の申請にはどの操作も撥ねられ、初回の記録が残ること")
  void terminalApplications_rejectEveryOperation() {
    for (OrderApplicationStatus terminal :
        List.of(
            OrderApplicationStatus.CONFIRMED,
            OrderApplicationStatus.DECLINED,
            OrderApplicationStatus.WITHDRAWN)) {
      OrderApplication application =
          OrderApplication.builder().status(terminal).businessDate(TODAY).build();
      assertThatThrownBy(() -> application.confirmWith("order-2", 8L, AT, TODAY))
          .as("状態 %s への確定が拒否されること", terminal)
          .isInstanceOf(InvalidOrderApplicationOperationException.class);
      assertThatThrownBy(() -> application.decline("二度目の理由", 8L, AT, TODAY))
          .as("状態 %s への謝絶が拒否されること", terminal)
          .isInstanceOf(InvalidOrderApplicationOperationException.class);
      assertThatThrownBy(() -> application.withdraw(8L, AT))
          .as("状態 %s への取り下げが拒否されること", terminal)
          .isInstanceOf(InvalidOrderApplicationOperationException.class);
      assertThat(application.getStatus()).as("拒否時に状態が変わらないこと").isEqualTo(terminal);
      assertThat(application.getProcessedBy()).as("拒否時に実行者が書かれないこと").isNull();
    }
  }

  @Test
  @DisplayName("希望日を過ぎた PENDING は失効となり、確定・謝絶が拒否されること")
  void expiredPending_rejectsConfirmationAndDecline() {
    OrderApplication application = pendingFor(TODAY.minusDays(1));

    assertThat(application.isExpired(TODAY)).isTrue();
    assertThatThrownBy(() -> application.confirmWith("order-1", 7L, AT, TODAY))
        .isInstanceOf(InvalidOrderApplicationOperationException.class)
        .hasMessageContaining("失効");
    assertThatThrownBy(() -> application.decline("理由", 7L, AT, TODAY))
        .isInstanceOf(InvalidOrderApplicationOperationException.class)
        .hasMessageContaining("失効");
    assertThat(application.getStatus())
        .as("失効は導出であり、行の状態は PENDING のまま動かないこと")
        .isEqualTo(OrderApplicationStatus.PENDING);
  }

  @Test
  @DisplayName("希望日を過ぎた PENDING でも本人は取り下げられること")
  void expiredPending_isStillWithdrawable() {
    OrderApplication application = pendingFor(TODAY.minusDays(1));
    application.withdraw(42L, AT);
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.WITHDRAWN);
  }

  @Test
  @DisplayName("希望日当日はまだ失効ではなく、確定できること")
  void sameDayPending_isNotExpired() {
    OrderApplication application = pendingFor(TODAY);
    assertThat(application.isExpired(TODAY)).isFalse();
    application.confirmWith("order-1", 7L, AT, TODAY);
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.CONFIRMED);
  }

  @Test
  @DisplayName("失効の導出は PENDING に限られ、終端の申請は希望日を過ぎても失効と呼ばないこと")
  void expiry_appliesOnlyToPending() {
    assertThat(
            OrderApplication.isExpired(OrderApplicationStatus.CONFIRMED, TODAY.minusDays(1), TODAY))
        .isFalse();
    assertThat(
            OrderApplication.isExpired(OrderApplicationStatus.PENDING, TODAY.minusDays(1), TODAY))
        .isTrue();
  }

  @Test
  @DisplayName("受注 id・実行者・時刻のいずれかを欠く確定は撥ねられること")
  void confirmWith_requiresOrderActorAndTime() {
    assertThatThrownBy(() -> pendingFor(TODAY).confirmWith(null, 7L, AT, TODAY))
        .isInstanceOf(InvalidOrderApplicationOperationException.class);
    assertThatThrownBy(() -> pendingFor(TODAY).confirmWith("order-1", null, AT, TODAY))
        .isInstanceOf(InvalidOrderApplicationOperationException.class);
    assertThatThrownBy(() -> pendingFor(TODAY).confirmWith("order-1", 7L, null, TODAY))
        .isInstanceOf(InvalidOrderApplicationOperationException.class);
  }

  @Test
  @DisplayName("終端判定は PENDING 以外のすべてであること")
  void isTerminal_coversEverythingButPending() {
    assertThat(OrderApplicationStatus.PENDING.isTerminal()).isFalse();
    assertThat(OrderApplicationStatus.CONFIRMED.isTerminal()).isTrue();
    assertThat(OrderApplicationStatus.DECLINED.isTerminal()).isTrue();
    assertThat(OrderApplicationStatus.WITHDRAWN.isTerminal()).isTrue();
  }
}
