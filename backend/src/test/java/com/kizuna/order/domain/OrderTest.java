package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

  private Order orderWithStatus(OrderStatus status) {
    return Order.builder().status(status).build();
  }

  @Test
  @DisplayName("確認済みの注文を完了すると会計金額とポイントが確定すること")
  void completeWith_fromConfirmed() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);

    order.completeWith(12000, 500, 120);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getTotalFee()).isEqualTo(12000);
    assertThat(order.getUsedPoints()).isEqualTo(500);
    assertThat(order.getAutoGrantPoints()).isEqualTo(120);
  }

  @Test
  @DisplayName("キャンセル済みの注文は完了できないこと")
  void completeWith_fromCancelled_isRejected() {
    Order order = orderWithStatus(OrderStatus.CANCELLED);
    assertThatThrownBy(() -> order.completeWith(12000, 0, 120))
        .isInstanceOf(IllegalOrderStateTransitionException.class)
        .hasMessageContaining("CANCELLED")
        .hasMessageContaining("COMPLETED");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getTotalFee()).as("撥ねた完了は会計金額を書かないこと").isNull();
  }

  @Test
  @DisplayName("完了済みの注文を再度完了できず、確定済みの会計も上書きされないこと")
  void completeWith_isNotIdempotent() {
    // 完了は台帳記帳と不可分のため、同一状態への静默冪等（transitionTo）に委ねると二重記帳になる
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.completeWith(12000, 500, 120);

    assertThatThrownBy(() -> order.completeWith(9999, 100, 99))
        .isInstanceOf(IllegalOrderStateTransitionException.class)
        .hasMessageContaining("COMPLETED");
    assertThat(order.getTotalFee()).isEqualTo(12000);
    assertThat(order.getUsedPoints()).isEqualTo(500);
    assertThat(order.getAutoGrantPoints()).isEqualTo(120);
  }

  @Test
  @DisplayName("確定済みの注文を理由付きで取消でき、理由・実行者・時刻が残ること")
  void cancelWith_fromConfirmed_recordsReasonActorAndTime() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    OffsetDateTime at = OffsetDateTime.parse("2026-08-14T17:42:00+09:00");

    order.cancelWith("客都合。当日夕方に体調不良の連絡あり", 7L, at);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getCancelledReason()).isEqualTo("客都合。当日夕方に体調不良の連絡あり");
    assertThat(order.getCancelledBy()).isEqualTo(7L);
    assertThat(order.getCancelledAt()).isEqualTo(at);
  }

  @Test
  @DisplayName("二度目の取消は撥ねられ、初回の理由と実行者が上書きされないこと")
  void cancelWith_secondTime_isRejectedAndFirstRecordKept() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    OffsetDateTime first = OffsetDateTime.parse("2026-08-14T17:42:00+09:00");
    order.cancelWith("初回の理由", 7L, first);

    assertThatThrownBy(() -> order.cancelWith("二度目の理由", 8L, first.plusHours(1)))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThat(order.getCancelledReason()).isEqualTo("初回の理由");
    assertThat(order.getCancelledBy()).isEqualTo(7L);
    assertThat(order.getCancelledAt()).isEqualTo(first);
  }

  @Test
  @DisplayName("完了済みの注文は専用取消の経路では取り消せないこと")
  void cancelWith_outsideConfirmed_isRejected() {
    // 定義域は CONFIRMED → CANCELLED のみ。未処理の予約申請は申請側の謝絶が受け持ち、
    // 誤完了の救済経路はまだ存在しない（ADR 0013）
    Order order = orderWithStatus(OrderStatus.COMPLETED);
    assertThatThrownBy(() -> order.cancelWith("理由", 7L, OffsetDateTime.now()))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThat(order.getStatus()).as("拒否時に状態が変わらないこと").isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getCancelledReason()).as("拒否時に理由が書かれないこと").isNull();
  }

  @Test
  @DisplayName("理由・実行者・時刻のいずれかを欠く取消は撥ねられること")
  void cancelWith_requiresReasonActorAndTime() {
    OffsetDateTime at = OffsetDateTime.now();
    assertThatThrownBy(() -> orderWithStatus(OrderStatus.CONFIRMED).cancelWith(null, 7L, at))
        .isInstanceOf(InvalidOrderCancellationException.class);
    assertThatThrownBy(() -> orderWithStatus(OrderStatus.CONFIRMED).cancelWith("  ", 7L, at))
        .isInstanceOf(InvalidOrderCancellationException.class);
    assertThatThrownBy(() -> orderWithStatus(OrderStatus.CONFIRMED).cancelWith("理由", null, at))
        .isInstanceOf(InvalidOrderCancellationException.class);
    assertThatThrownBy(() -> orderWithStatus(OrderStatus.CONFIRMED).cancelWith("理由", 7L, null))
        .isInstanceOf(InvalidOrderCancellationException.class);
  }

  @Test
  @DisplayName("キャンセル済みの注文からは一切遷移できないこと")
  void transitions_fromCancelled_areRejected() {
    Order order = orderWithStatus(OrderStatus.CANCELLED);
    assertThatThrownBy(() -> order.transitionTo(OrderStatus.CONFIRMED))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThatThrownBy(() -> order.transitionTo(OrderStatus.COMPLETED))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
  }

  @Test
  @DisplayName("部分更新で人数を変更でき、null は変更しないこと")
  void apply_pax() {
    Order order = Order.builder().status(OrderStatus.CONFIRMED).pax(2).build();

    order.apply(patchWithPax(5));
    assertThat(order.getPax()).isEqualTo(5);

    order.apply(patchWithPax(null));
    assertThat(order.getPax()).as("null の人数は変更しないこと").isEqualTo(5);
  }

  private OrderPatch patchWithPax(Integer pax) {
    return new OrderPatch(
        null, null, null, pax, null, null, null, null, null, null, null, null, null, null, null);
  }

  @Test
  @DisplayName("部分更新で営業日・場所・媒体を変更できること")
  void apply_businessDateLocationAndMedia() {
    // 改期（営業日の変更）を編集で行えないと、取消して再登録する運用になり取消の記録が雑音で汚れる
    Order order =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .businessDate(LocalDate.parse("2026-08-15"))
            .locationAddress("誤記の住所")
            .build();

    order.apply(
        new OrderPatch(
            LocalDate.parse("2026-08-20"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "中央区銀座 1-2-3",
            "グランドホテル 1204",
            "ドコモ",
            "自社サイト",
            null,
            null));

    assertThat(order.getBusinessDate()).isEqualTo(LocalDate.parse("2026-08-20"));
    assertThat(order.getLocationAddress()).isEqualTo("中央区銀座 1-2-3");
    assertThat(order.getLocationBuilding()).isEqualTo("グランドホテル 1204");
    assertThat(order.getCarrier()).isEqualTo("ドコモ");
    assertThat(order.getMediaName()).isEqualTo("自社サイト");
  }

  @Test
  @DisplayName("顧客の着いていない受注の連絡先を訂正でき、null は変更しないこと")
  void correctContact_onUnlinkedOrder() {
    Order order = Order.builder().contactName("誤記の名前").contactPhoneNumber("09011112222").build();

    order.correctContact("正しい名前", null);

    assertThat(order.getContactName()).isEqualTo("正しい名前");
    assertThat(order.getContactPhoneNumber()).as("null の項目は変更しないこと").isEqualTo("09011112222");
  }

  @Test
  @DisplayName("顧客の着いた受注の連絡先訂正は撥ねられること（黙って捨てない）")
  void correctContact_onLinkedOrder_isRejected() {
    // 着いていれば名乗りの正本は台帳の行。黙って捨てると送り手は直ったと誤解したまま誤記が残る
    Order linked = Order.builder().customerId("c1").build();

    assertThatThrownBy(() -> linked.correctContact("受注側から書こうとした名前", "09099998888"))
        .isInstanceOf(InvalidOrderContactCorrectionException.class);
    assertThat(linked.getContactName()).isNull();
    assertThat(linked.getContactPhoneNumber()).isNull();
  }

  @Test
  @DisplayName("終端状態は完了・取消の 2 つで、確定は終端でないこと")
  void isTerminal_coversCompletedAndCancelled() {
    assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
    assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
    assertThat(OrderStatus.CONFIRMED.isTerminal()).isFalse();
  }

  @Test
  @DisplayName("連絡先の写しは顧客が着いていない受注にだけ入ること")
  void recordContactIfUnlinked_onlyWhenNoCustomer() {
    Order unlinked = Order.builder().build();

    unlinked.recordContactIfUnlinked("重複照合の来客", "09012345678");
    assertThat(unlinked.getContactName()).isEqualTo("重複照合の来客");
    assertThat(unlinked.getContactPhoneNumber()).isEqualTo("09012345678");

    // 台帳の行が名乗りを持つ受注に写しを重ねると、どちらが正本かが読み手から消える
    Order linked = Order.builder().customerId("c1").build();

    linked.recordContactIfUnlinked("重複照合の来客", "09012345678");
    assertThat(linked.getContactName()).isNull();
    assertThat(linked.getContactPhoneNumber()).isNull();
  }

  @Test
  @DisplayName("同じステータスへの遷移は何もしない（冪等）こと")
  void transitionTo_sameStatus_isNoOp() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.transitionTo(OrderStatus.CONFIRMED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }
}
