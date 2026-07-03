package com.kizuna.order.domain;

/** 注文ステータス。遷移は CREATED → CONFIRMED → COMPLETED、キャンセルは完了前のみ。 */
public enum OrderStatus {
  CREATED,
  CONFIRMED,
  COMPLETED,
  CANCELLED;

  boolean canTransitionTo(OrderStatus target) {
    return switch (this) {
      case CREATED -> target == CONFIRMED || target == CANCELLED;
      case CONFIRMED -> target == COMPLETED || target == CANCELLED;
      case COMPLETED, CANCELLED -> false;
    };
  }
}
