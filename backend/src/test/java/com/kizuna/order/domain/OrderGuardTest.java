package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderGuardTest {
  private static Order order(OrderStatus status, boolean invalidated) {
    return Order.builder().status(status).completionInvalidated(invalidated).build();
  }

  @Test
  @DisplayName("訂正できるのは完了済みで無効化されていない受注だけであること")
  void correctableOnlyWhenCompletedAndNotInvalidated() {
    assertThatCode(() -> order(OrderStatus.COMPLETED, false).requireCorrectable())
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> order(OrderStatus.COMPLETED, true).requireCorrectable())
        .isInstanceOf(InvalidOrderCorrectionException.class)
        .hasMessage("完了した受注だけが訂正できます");
    assertThatThrownBy(() -> order(OrderStatus.CONFIRMED, false).requireCorrectable())
        .isInstanceOf(InvalidOrderCorrectionException.class)
        .hasMessage("完了した受注だけが訂正できます");
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"COMPLETED", "CANCELLED"})
  @DisplayName("終端の受注は編集できないこと")
  void terminalOrderIsNotEditable(OrderStatus terminal) {
    assertThatThrownBy(() -> order(terminal, false).requireEditable())
        .isInstanceOf(InvalidOrderFeeLineException.class)
        .hasMessage("終端の内容は専用訂正で変更してください");
  }

  @Test
  @DisplayName("未完了の受注は編集できること")
  void unfinishedOrderIsEditable() {
    assertThatCode(() -> order(OrderStatus.CONFIRMED, false).requireEditable())
        .doesNotThrowAnyException();
    assertThatCode(() -> order(OrderStatus.IN_SERVICE, false).requireEditable())
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("ポイントを巻き戻せるのは完了済みの受注だけであること")
  void pointRollbackRequiresCompleted() {
    assertThatCode(() -> order(OrderStatus.COMPLETED, false).requirePointRollbackable())
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> order(OrderStatus.CONFIRMED, false).requirePointRollbackable())
        .isInstanceOf(InvalidOrderFeeLineException.class)
        .hasMessage("完了した受注の利用だけを一度相殺できます");
  }

  @Test
  @DisplayName("伝票を再発行できるのは完了済みで無効化されていない受注だけであること")
  void receiptReissuableOnlyWhenCompletedAndNotInvalidated() {
    assertThatCode(() -> order(OrderStatus.COMPLETED, false).requireReceiptReissuable())
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> order(OrderStatus.CONFIRMED, false).requireReceiptReissuable())
        .isInstanceOf(InvalidOrderCorrectionException.class)
        .hasMessage("完了していない受注に伝票は発行できません");
    assertThatThrownBy(() -> order(OrderStatus.COMPLETED, true).requireReceiptReissuable())
        .isInstanceOf(InvalidOrderCorrectionException.class)
        .hasMessage("無効化した受注に伝票は発行できません");
  }
}
