package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCorrectionTest {

  private Order completedOrder() {
    Order order =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .course(OrderCourses.course("60 分コース", 60, 12000))
            .build();
    order.replaceStoreFeeLines(
        List.of(new OrderFeeLineDraft(OrderFeeLineKind.CREDIT_SURCHARGE, "オプション A", 2000)));

    order.completeWith(500, 140);
    return order;
  }

  @Test
  @DisplayName("訂正前後の快照は後続の訂正で変わらず、ポイント行と報酬を保持すること")
  void snapshotsRemainImmutableAcrossLaterCorrections() {
    Order order = completedOrder();
    var before = OrderCorrectionSnapshot.of(order);
    order.correct(
        new OrderCorrectionCommand(
            null, LocalTime.of(22, 40), OrderCourses.course("120 分コース", 120, 22000), List.of()));
    var after = OrderCorrectionSnapshot.of(order);
    order.correct(
        new OrderCorrectionCommand(
            null, null, OrderCourses.course("90 分コース", 90, 16000), List.of()));
    assertThat(before.totalFee()).isEqualTo(13500);
    assertThat(before.course().name()).isEqualTo("60 分コース");
    assertThat(before.feeLines())
        .extracting(OrderFeeLineSnapshot::kind, OrderFeeLineSnapshot::amount)
        .containsExactly(
            tuple(OrderFeeLineKind.BASE_COURSE, 12000),
            tuple(OrderFeeLineKind.CREDIT_SURCHARGE, 2000),
            tuple(OrderFeeLineKind.POINT_REDEMPTION, -500));
    assertThat(after.totalFee()).isEqualTo(21500);
    assertThat(after.actualEndTime()).isEqualTo(LocalTime.of(22, 40));
    assertThat(after.accruedRemuneration()).isEqualTo(after.totalRemuneration());
    assertThatThrownBy(() -> before.feeLines().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("合計が負になる訂正は門の固有の文言で撥ねられること")
  void correct_rejectsATotalBelowTheRedeemedPoints() {
    // 総和 0 以上は全経路共通の不変量だが、門は利用の行を動かせないため差を吸収する先が無い。
    // 一般の差し替えの文言（割引・調整を見直せ）では、門の中で何をすべきかが伝わらない
    Order order = completedOrder();

    assertThatThrownBy(
            () ->
                order.correct(
                    new OrderCorrectionCommand(
                        null, null, OrderCourses.course("60 分コース", 60, 499), List.of())))
        .isInstanceOf(InvalidOrderFeeLineException.class)
        .hasMessage("訂正後の請求額が利用ポイントを下回ります。ポイント利用の訂正はポイント機構で行ってください");
  }
}
