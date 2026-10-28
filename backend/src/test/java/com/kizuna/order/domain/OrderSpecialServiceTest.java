package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderSpecialServiceTest {
  @Test
  void adoptedSpecialServiceRowsCarryTheOrderStore() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).castId("cast").build();
    order.setStoreId(7L);
    order.adoptServices(
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
        List.of(special("special", 2000, 1500)),
        List.of());

    assertThat(order.getFeeLines())
        .hasSize(2)
        .allSatisfy(line -> assertThat(line.getStoreId()).isEqualTo(7L));
    @SuppressWarnings("unchecked")
    var rows =
        (List<OrderSpecialService>) ReflectionTestUtils.getField(order, "specialServiceLines");
    assertThat(rows).singleElement().extracting(OrderSpecialService::getStoreId).isEqualTo(7L);
  }

  @Test
  void rejectionAndResolutionEventsCarryTheOrderStore() {
    var order = Order.builder().status(OrderStatus.CONFIRMED).castId("cast").build();
    order.setId("o1");
    order.setStoreId(7L);

    var rejectedAt = OffsetDateTime.parse("2026-08-14T17:42:00+09:00");
    var resolvedAt = rejectedAt.plusHours(1);
    var rejected =
        OrderSpecialServiceEvent.rejected(
            order, List.of(), "cast", "special", "consent", 1L, rejectedAt);
    var resolved = rejected.resolved(order, List.of(), List.of(), 0, "REMOVED", 1L, resolvedAt);

    assertThat(rejected.getOccurredAt()).isEqualTo(rejectedAt);
    assertThat(resolved.getOccurredAt()).isEqualTo(resolvedAt);

    assertThat(rejected.getStoreId()).isEqualTo(7L);
    assertThat(resolved.getStoreId()).isEqualTo(7L);
  }

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
