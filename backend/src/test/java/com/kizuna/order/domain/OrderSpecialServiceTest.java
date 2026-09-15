package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderSpecialServiceTest {
  @Test
  void specialServicesHaveFixedFeesWithoutChangingCourseDuration() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).castId("cast").build();
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
    order.adoptServices(
        course,
        List.of(special("special", 2000, 1500), special("free", 0, 0)),
        List.of(new OrderFeeLineDraft(OrderFeeLineKind.DISCOUNT, "割引", -2000)));
    assertThat(order.getTotalFee()).isEqualTo(12000);
    assertThat(order.getCourse().durationMinutes()).isEqualTo(60);
    assertThat(order.getSpecialServices()).hasSize(2);
    assertThat(order.getFeeLines())
        .filteredOn(l -> l.getKind() == OrderFeeLineKind.SPECIAL_SERVICE)
        .extracting(OrderFeeLine::getRemuneration)
        .containsExactly(1500, 0);
    assertThatThrownBy(
            () ->
                order.adoptServices(
                    course,
                    List.of(special("special", 2000, 1500), special("special", 2000, 1500)),
                    List.of()))
        .isInstanceOf(InvalidOrderFeeLineException.class);
  }

  @Test
  void removalRejectsExcessDiscountAndStartingDoesNotPreventRepair() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).castId("cast").build();
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
    order.adoptServices(
        course,
        List.of(special("special", 2000, 1500)),
        List.of(new OrderFeeLineDraft(OrderFeeLineKind.DISCOUNT, "割引", -13000)));
    assertThat(order.getTotalFee()).isEqualTo(1000);
    order.start("提供開始", 1L, OffsetDateTime.now());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_SERVICE);
    assertThatThrownBy(() -> order.start("再開始", 1L, OffsetDateTime.now()))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThatThrownBy(() -> order.adoptServices(course, List.of(), null))
        .isInstanceOf(InvalidOrderFeeLineException.class);
  }

  @Test
  void specialServiceMoneyAndAdoptionEvidenceAreValidated() {
    assertThatThrownBy(() -> special("s", 0, 1)).isInstanceOf(InvalidOrderFeeLineException.class);
    assertThatThrownBy(() -> special("s", 10, -1)).isInstanceOf(InvalidOrderFeeLineException.class);
    assertThatThrownBy(() -> special("s", 10, 11)).isInstanceOf(InvalidOrderFeeLineException.class);
  }

  static SpecialServiceSnapshot special(String id, int price, int remuneration) {
    return new SpecialServiceSnapshot(
        id,
        id + "-revision",
        1,
        1,
        "特殊サービス",
        price == 0 ? "FREE" : "PAID",
        price,
        remuneration,
        "ACCEPTED_TERMS",
        OffsetDateTime.now(),
        "cast",
        "consent",
        1L);
  }
}
