package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class OrderCompletionGuardTest {
  private static final long VERSION = 4L;

  // 版は JPA が管理し設定子を持たない。null のままだと null 同士の一致で照合が素通りする
  private static Order order(OrderStatus status, int charge) {
    Order order = Order.builder().course(OrderCourses.course("基本", 60, 0)).status(status).build();
    order.replaceStoreFeeLines(
        List.of(new OrderFeeLineDraft(OrderFeeLineKind.CREDIT_SURCHARGE, "会計", charge)));
    ReflectionTestUtils.setField(order, "version", VERSION);
    return order;
  }

  @Test
  @DisplayName("版がずれた要求は expected_version を指す競合で撥ねること")
  void staleVersionIsAConflictNamingTheField() {
    Order order = order(OrderStatus.CONFIRMED, 1000);

    assertThatCode(() -> order.requireVersion(VERSION)).doesNotThrowAnyException();
    assertThatThrownBy(() -> order.requireVersion(VERSION - 1))
        .isInstanceOfSatisfying(
            OrderVersionConflictException.class,
            ex -> assertThat(ex.details()).containsOnlyKeys("expected_version"));
    assertThatThrownBy(() -> order.requireVersion(null))
        .isInstanceOf(OrderVersionConflictException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"COMPLETED", "CANCELLED"})
  @DisplayName("終端の受注へ陳腐化した版で届く完了は状態の誤りではなく競合として撥ねること")
  void staleVersionWinsOverTerminalStatus(OrderStatus terminal) {
    Order order = order(terminal, 1000);

    assertThatThrownBy(() -> order.requireCompletable(VERSION - 1))
        .isInstanceOf(OrderVersionConflictException.class);
    assertThatThrownBy(() -> order.requireCompletable(VERSION))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
  }

  @Test
  @DisplayName("未完了で版の揃った受注は完了の前提を満たすこと")
  void unfinishedOrderAtTheCurrentVersionIsCompletable() {
    assertThatCode(() -> order(OrderStatus.CONFIRMED, 1000).requireCompletable(VERSION))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("会員へ紐づかない受注はポイントを使えず、利用しない完了は通すこと")
  void nonMemberCannotRedeem() {
    Order order = order(OrderStatus.CONFIRMED, 1000);

    assertThatThrownBy(() -> order.requireRedeemable(100, false))
        .isInstanceOf(InvalidOrderFeeLineException.class)
        .hasMessage("非会員の受注ではポイントを利用できません");
    assertThatCode(() -> order.requireRedeemable(0, false)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("利用ポイントは 0 以上かつ会計金額以下で、全額のポイント払いは通すこと")
  void redemptionIsBoundedByTheGrantBasis() {
    Order order = order(OrderStatus.CONFIRMED, 1000);

    assertThatThrownBy(() -> order.requireRedeemable(-1, true))
        .isInstanceOf(InvalidOrderFeeLineException.class)
        .hasMessage("利用ポイントは 0 以上です");
    assertThatThrownBy(() -> order.requireRedeemable(1001, true))
        .isInstanceOf(InvalidOrderFeeLineException.class)
        .hasMessage("利用ポイントは会計金額を超えられません");
    assertThatCode(() -> order.requireRedeemable(1000, true)).doesNotThrowAnyException();
  }
}
