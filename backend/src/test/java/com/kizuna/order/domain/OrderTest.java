package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  @DisplayName("同じステータスへの遷移は何もしない（冪等）こと")
  void transitionTo_sameStatus_isNoOp() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.transitionTo(OrderStatus.CONFIRMED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }
}
