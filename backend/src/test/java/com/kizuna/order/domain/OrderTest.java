package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

  private Order orderWithStatus(OrderStatus status) {
    return Order.builder().status(status).build();
  }

  @Test
  @DisplayName("作成直後の注文を確認済みにできること")
  void confirm_fromCreated() {
    Order order = orderWithStatus(OrderStatus.CREATED);
    order.confirm();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  @DisplayName("確認済みの注文を完了できること")
  void complete_fromConfirmed() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.complete();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
  }

  @Test
  @DisplayName("確認を飛ばして注文を完了できないこと")
  void complete_fromCreated_isRejected() {
    Order order = orderWithStatus(OrderStatus.CREATED);
    assertThatThrownBy(order::complete)
        .isInstanceOf(IllegalOrderStateTransitionException.class)
        .hasMessageContaining("CREATED")
        .hasMessageContaining("COMPLETED");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
  }

  @Test
  @DisplayName("作成直後・確認済みの注文をキャンセルできること")
  void cancel_beforeCompletion() {
    Order created = orderWithStatus(OrderStatus.CREATED);
    created.cancel();
    assertThat(created.getStatus()).isEqualTo(OrderStatus.CANCELLED);

    Order confirmed = orderWithStatus(OrderStatus.CONFIRMED);
    confirmed.cancel();
    assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  @DisplayName("完了済みの注文はキャンセルできないこと")
  void cancel_fromCompleted_isRejected() {
    Order order = orderWithStatus(OrderStatus.COMPLETED);
    assertThatThrownBy(order::cancel).isInstanceOf(IllegalOrderStateTransitionException.class);
  }

  @Test
  @DisplayName("キャンセル済みの注文からは一切遷移できないこと")
  void transitions_fromCancelled_areRejected() {
    Order order = orderWithStatus(OrderStatus.CANCELLED);
    assertThatThrownBy(order::confirm).isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThatThrownBy(order::complete).isInstanceOf(IllegalOrderStateTransitionException.class);
  }

  @Test
  @DisplayName("未確定の申請を取り下げられること")
  void cancelRequest_fromCreated() {
    Order order = orderWithStatus(OrderStatus.CREATED);
    order.cancelRequest();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  @DisplayName("確定済み・完了済み・キャンセル済みの注文は申請取り下げの経路では取り消せないこと")
  void cancelRequest_afterConfirmation_isRejected() {
    for (OrderStatus status :
        List.of(OrderStatus.CONFIRMED, OrderStatus.COMPLETED, OrderStatus.CANCELLED)) {
      Order order = orderWithStatus(status);
      assertThatThrownBy(order::cancelRequest)
          .as("状態 %s からの申請取り下げが拒否されること", status)
          .isInstanceOf(IllegalOrderStateTransitionException.class);
      assertThat(order.getStatus()).as("拒否時に状態が変わらないこと").isEqualTo(status);
    }
  }

  @Test
  @DisplayName("Web 受付かつ申請者の会員コードが揃って初めて予約申請と判定されること")
  void isReservationRequest_requiresRouteAndRequesterSnapshot() {
    assertThat(
            Order.builder()
                .receptionRoute(ReceptionRoute.WEB)
                .requesterMemberCode("123456789012")
                .build()
                .isReservationRequest())
        .isTrue();
    assertThat(Order.builder().build().isReservationRequest())
        .as("受付経路の記録が無い受注は申請ではないこと")
        .isFalse();
    assertThat(Order.builder().receptionRoute(ReceptionRoute.WEB).build().isReservationRequest())
        .as("店舗が WEB を手入力しただけの受注は申請ではないこと")
        .isFalse();
    assertThat(
            Order.builder()
                .receptionRoute(ReceptionRoute.PHONE)
                .requesterMemberCode("123456789012")
                .build()
                .isReservationRequest())
        .as("電話受付は申請ではないこと")
        .isFalse();
  }

  @Test
  @DisplayName("部分更新で人数を変更でき、null は変更しないこと")
  void apply_pax() {
    Order order = Order.builder().status(OrderStatus.CREATED).pax(2).build();

    order.apply(patchWithPax(5));
    assertThat(order.getPax()).isEqualTo(5);

    order.apply(patchWithPax(null));
    assertThat(order.getPax()).as("null の人数は変更しないこと").isEqualTo(5);
  }

  private OrderPatch patchWithPax(Integer pax) {
    return new OrderPatch(null, null, pax, null, null, null, null, null, null, null, null, null);
  }

  @Test
  @DisplayName("申請の書き換えは渡した値で置き換え、null は未設定にすること")
  void revise_replacesInsteadOfPatching() {
    Order order =
        Order.builder()
            .status(OrderStatus.CREATED)
            .receptionistId(3L)
            .castId("cast-1")
            .pax(2)
            .remarks("元の備考")
            .build();

    order.revise(4L, "cast-2", 5, "書き換えた備考");
    assertThat(order.getReceptionistId()).isEqualTo(4L);
    assertThat(order.getCastId()).isEqualTo("cast-2");
    assertThat(order.getPax()).isEqualTo(5);
    assertThat(order.getRemarks()).isEqualTo("書き換えた備考");

    // 部分更新（apply）では消せない項目を、この経路では空に戻せる
    order.revise(null, null, 5, null);
    assertThat(order.getReceptionistId()).isNull();
    assertThat(order.getCastId()).isNull();
    assertThat(order.getRemarks()).isNull();
  }

  @Test
  @DisplayName("同じステータスへの遷移は何もしない（冪等）こと")
  void transitionTo_sameStatus_isNoOp() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.transitionTo(OrderStatus.CONFIRMED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }
}
