package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderCompletionRemunerationTest {
  @Test
  void completionAccruesFixedRemunerationOnOriginalBusinessDate() {
    var date = LocalDate.of(2026, 9, 14);
    var order = Order.builder().status(OrderStatus.CONFIRMED).businessDate(date).build();
    order.adoptCourse(
        new OrderCourse(
            "course",
            "revision",
            1,
            "コース",
            60,
            12000,
            7000,
            "CURRENT_SETTING",
            OffsetDateTime.now()),
        List.of(
            OrderFeeLineDraft.extension("延長", 30, 3000, 2000),
            new OrderFeeLineDraft(OrderFeeLineKind.DISCOUNT, "割引", -2000)));
    assertThat(order.getAccruedRemuneration()).isZero();
    assertThat(order.getCompletedAt()).isNull();
    var before = OffsetDateTime.now();
    order.completeWith(3000, 130);
    assertThat(order.getAccruedRemuneration()).isEqualTo(9000);
    assertThat(order.getCompletedAt()).isBetween(before, OffsetDateTime.now());
    assertThat(order.getBusinessDate()).isEqualTo(date);
    assertThat(order.getTotalFee()).isEqualTo(10000);
    assertThat(order.grantBasisAmount()).isEqualTo(13000);
  }

  @Test
  void specialServiceAndDiscountKeepItemRemunerationAndCancellationAccruesNothing() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).build();
    var course =
        new OrderCourse(
            "course",
            "revision",
            1,
            "コース",
            60,
            12000,
            7000,
            "CURRENT_SETTING",
            OffsetDateTime.now());
    var extras =
        List.of(
            OrderFeeLineDraft.extension("延長", 30, 3000, 2000),
            new OrderFeeLineDraft(
                null,
                OrderFeeLineKind.SURCHARGE,
                "加算",
                1000,
                null,
                0,
                new OrderServiceAdoption(
                    "surcharge", "surcharge-r", 1, "CURRENT_SETTING", OffsetDateTime.now())),
            new OrderFeeLineDraft(OrderFeeLineKind.DISCOUNT, "割引", -2000));
    var special =
        new SpecialServiceSnapshot(
            "special",
            "special-r",
            1,
            1,
            "特殊サービス",
            "PAID",
            2000,
            1500,
            "ACCEPTED_TERMS",
            OffsetDateTime.now(),
            "cast",
            "consent",
            1L);
    order.adoptServices(course, List.of(special), extras);
    assertThat(order.getTotalFee()).isEqualTo(16000);
    assertThat(order.getTotalRemuneration()).isEqualTo(10500);
    order.adoptServices(course, List.of(), extras);
    assertThat(order.getTotalFee()).isEqualTo(14000);
    assertThat(order.getTotalRemuneration()).isEqualTo(9000);
    order.cancelWith("取消", 1L, OffsetDateTime.now());
    assertThat(order.getAccruedRemuneration()).isZero();
    assertThat(order.getCompletedAt()).isNull();
  }

  @Test
  void correctionUpdatesAccrualButPreservesCompletionTime() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).build();
    order.adoptCourse(
        new OrderCourse(
            "course",
            "revision",
            1,
            "コース",
            60,
            12000,
            7000,
            "CURRENT_SETTING",
            OffsetDateTime.now()),
        List.of());
    order.completeWith(3000, 120);
    var completedAt = order.getCompletedAt();
    order.correct(
        new OrderCorrectionCommand(
            null, null, null, List.of(OrderFeeLineDraft.extension("延長", 30, 3000, 2000))));
    assertThat(order.getAccruedRemuneration()).isEqualTo(9000);
    assertThat(order.getCompletedAt()).isEqualTo(completedAt);
  }
}
