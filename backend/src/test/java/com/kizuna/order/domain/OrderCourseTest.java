package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderCourseTest {
  @Test
  void changingCourseKeepsOtherLinesAndDoesNotReduceRemunerationForDiscounts() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).build();
    order.adoptCourse(
        course("初回", 12000, 7000),
        List.of(new OrderFeeLineDraft(OrderFeeLineKind.DISCOUNT, "割引", -2000)));
    assertThat(order.getTotalFee()).isEqualTo(10000);
    order.adoptCourse(course("変更", 15000, 9000), null);
    assertThat(order.getTotalFee()).isEqualTo(13000);
    assertThat(order.getCourse().remuneration()).isEqualTo(9000);
    assertThat(order.getFeeLines()).hasSize(2);
    assertThatThrownBy(
            () ->
                order.replaceStoreFeeLines(
                    List.of(new OrderFeeLineDraft(OrderFeeLineKind.BASE_COURSE, "上書き", 1))))
        .isInstanceOf(InvalidOrderFeeLineException.class);
  }

  private OrderCourse course(String name, int price, int remuneration) {
    return new OrderCourse(
        "service",
        "revision",
        1,
        name,
        60,
        price,
        remuneration,
        "CURRENT_SETTING",
        OffsetDateTime.now());
  }
}
