package com.kizuna.order.domain;

/** 注文ステータス。遷移は CREATED → CONFIRMED → COMPLETED、キャンセルは完了前のみ。 */
public enum OrderStatus {
  CREATED,
  CONFIRMED,
  COMPLETED,
  CANCELLED;

  /**
   * 終端状態か。完了と取消はどちらも記録として確定しており、以後内容が動くことはない（ADR 0013）。
   *
   * <p>汎用更新の守衛はこの述語ひとつで判定する。状態ごとに書き分けると同じ規則を二箇所に持たせることになり、 片方だけが更新される入口になる。
   */
  public boolean isTerminal() {
    return this == COMPLETED || this == CANCELLED;
  }

  boolean canTransitionTo(OrderStatus target) {
    return switch (this) {
      case CREATED -> target == CONFIRMED || target == CANCELLED;
      case CONFIRMED -> target == COMPLETED || target == CANCELLED;
      case COMPLETED, CANCELLED -> false;
    };
  }
}
