package com.kizuna.order.domain;

/** 受注は確定で出生し、開始・完了・取消は専用の操作が担う。 */
public enum OrderStatus {
  CONFIRMED,
  IN_SERVICE,
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
      case CONFIRMED -> target == IN_SERVICE || target == COMPLETED || target == CANCELLED;
      case IN_SERVICE -> target == COMPLETED || target == CANCELLED;
      case COMPLETED, CANCELLED -> false;
    };
  }
}
