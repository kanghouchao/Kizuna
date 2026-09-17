package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderCompletionInvalidationTest {
  @Test
  void invalidationRequiresOffsetAndPreservesOriginalFacts() {
    var order =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .businessDate(LocalDate.of(2026, 9, 14))
            .build();
    order.adoptCourse(
        new OrderCourse(
            "course",
            "revision",
            1,
            "コース",
            60,
            10000,
            6000,
            "CURRENT_SETTING",
            OffsetDateTime.now()),
        List.of());
    order.completeWith(3000, 100);
    var completed = order.getCompletedAt();
    assertThatThrownBy(order::invalidateCompletion).hasMessageContaining("ポイント");
    assertThat(order.isCompletionInvalidated()).isFalse();
    assertThat(order.getTotalFee()).isEqualTo(7000);
    order.offsetPointRedemption(3000);
    var lines = order.getFeeLines();
    order.invalidateCompletion();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.isCompletionInvalidated()).isTrue();
    assertThat(order.getTotalFee()).isZero();
    assertThat(order.getTotalRemuneration()).isZero();
    assertThat(order.getAccruedRemuneration()).isZero();
    assertThat(order.getFeeLines()).containsExactlyElementsOf(lines);
    assertThat(order.getCourse().price()).isEqualTo(10000);
    assertThat(order.getBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(order.getCompletedAt()).isEqualTo(completed);
    assertThat(order.getAutoGrantPoints()).isEqualTo(100);
    assertThatThrownBy(order::invalidateCompletion)
        .isInstanceOf(InvalidOrderCorrectionException.class);
    assertThatThrownBy(() -> order.correct(new OrderCorrectionCommand(null, null, null, List.of())))
        .isInstanceOf(InvalidOrderCorrectionException.class);
  }
}
