package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderExtensionTest {
  @Test
  void multipleExtensionsIncludeFreeTimeAndDiscountDoesNotReduceRemuneration() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).build();
    order.adoptCourse(
        OrderCourses.course("標準", 60, 12000),
        List.of(
            OrderFeeLineDraft.extension("延長1", 30, 3000, 2000),
            OrderFeeLineDraft.extension("無料延長", 15, 0, 0),
            new OrderFeeLineDraft(OrderFeeLineKind.DISCOUNT, "優待", -14000)));
    assertThat(order.getTotalFee()).isEqualTo(1000);
    assertThat(order.getTotalRemuneration()).isEqualTo(2000);
    assertThat(order.getTotalDurationMinutes()).isEqualTo(105);
    assertThat(order.getExtensionMinutes()).isEqualTo(45);
    assertThatThrownBy(() -> order.adoptCourse(OrderCourses.course("短時間", 30, 10000), null))
        .isInstanceOf(InvalidOrderFeeLineException.class);
  }

  @Test
  void extensionCannotBeRecordedWithoutMinutesAndRemuneration() {
    assertThatThrownBy(() -> OrderFeeLine.of(OrderFeeLineKind.EXTENSION, "延長", 3000))
        .isInstanceOf(InvalidOrderFeeLineException.class);
  }

  @Test
  void extensionBoundariesAndDuplicateConfiguredSurchargesAreRejected() {
    for (var draft :
        List.of(
            OrderFeeLineDraft.extension("延長", 0, 100, 0),
            OrderFeeLineDraft.extension("延長", -1, 100, 0),
            OrderFeeLineDraft.extension("延長", 1, -1, 0),
            OrderFeeLineDraft.extension("延長", 1, 100, -1),
            OrderFeeLineDraft.extension("延長", 1, 100, 101))) {
      var order = Order.builder().status(OrderStatus.CONFIRMED).build();
      assertThatThrownBy(
              () -> order.adoptCourse(OrderCourses.course("基本", 60, 12000), List.of(draft)))
          .isInstanceOf(InvalidOrderFeeLineException.class);
    }
    var adoption =
        new OrderServiceAdoption(
            "surcharge", "revision", 1, "CURRENT_SETTING", OffsetDateTime.now());
    var surcharge =
        new OrderFeeLineDraft(null, OrderFeeLineKind.SURCHARGE, "加算", 1000, null, 500, adoption);
    var order = Order.builder().status(OrderStatus.CONFIRMED).build();
    assertThatThrownBy(
            () ->
                order.adoptCourse(
                    OrderCourses.course("基本", 60, 12000), List.of(surcharge, surcharge)))
        .isInstanceOf(InvalidOrderFeeLineException.class);
    assertThatThrownBy(
            () ->
                order.adoptCourse(
                    OrderCourses.course("基本", 60, 12000),
                    List.of(OrderFeeLineDraft.extension("上限", Integer.MAX_VALUE, 0, 0))))
        .isInstanceOf(InvalidOrderFeeLineException.class);
  }
}
