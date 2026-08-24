package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCorrectionTest {

  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-08-23T21:00:00+09:00");

  private Order completedOrder() {
    Order order =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .courseName("60 分コース")
            .courseMinutes(60)
            .build();
    order.replaceStoreFeeLines(
        List.of(
            new OrderFeeLineDraft(OrderFeeLineKind.BASE_COURSE, null, 12000),
            new OrderFeeLineDraft(OrderFeeLineKind.OPTION, "オプション A", 2000)));
    order.completeWith(500, 140);
    return order;
  }

  @Test
  @DisplayName("快照は訂正前の標量と明細の全行を写し取ること")
  void snapshotOf_capturesTheScalarsAndEveryFeeLine() {
    Order before = completedOrder();

    OrderCorrection correction = OrderCorrection.snapshotOf(before, "金額の誤記", 7L, AT);

    assertThat(correction.getReason()).isEqualTo("金額の誤記");
    assertThat(correction.getCorrectedBy()).isEqualTo(7L);
    assertThat(correction.getCorrectedAt()).isEqualTo(AT);
    assertThat(correction.getCourseName()).isEqualTo("60 分コース");
    assertThat(correction.getCourseMinutes()).isEqualTo(60);
    assertThat(correction.getTotalFee()).isEqualTo(13500);
    // 門が触れない行（ポイント利用）も載せる。履歴行だけで訂正前の姿が読める形にしておかないと、
    // 現在の行を突き合わせなければ意味が決まらない記録になる。金額は保存されたとおりの帯符号
    assertThat(correction.getFeeLines())
        .extracting(OrderFeeLineSnapshot::kind, OrderFeeLineSnapshot::amount)
        .containsExactly(
            tuple(OrderFeeLineKind.BASE_COURSE, 12000),
            tuple(OrderFeeLineKind.OPTION, 2000),
            tuple(OrderFeeLineKind.POINT_REDEMPTION, -500));
  }

  @Test
  @DisplayName("訂正を当てた後に起こした快照は訂正後の姿を前値として記録してしまうこと")
  void snapshotOf_mustBeTakenBeforeTheCorrectionIsApplied() {
    // 管理下の集約はその場で書き換わる。順序を守らせる仕組みは型に無いので、性質として固定しておく
    Order order = completedOrder();
    order.correct(
        new OrderCorrectionCommand(
            null,
            LocalTime.of(22, 40),
            "120 分コース",
            120,
            null,
            List.of(new OrderFeeLineDraft(OrderFeeLineKind.BASE_COURSE, null, 22000))));

    OrderCorrection tooLate = OrderCorrection.snapshotOf(order, "順序を誤った快照", 7L, AT);

    assertThat(tooLate.getCourseName()).isEqualTo("120 分コース");
    assertThat(tooLate.getTotalFee()).isEqualTo(21500);
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
                        null,
                        null,
                        "60 分コース",
                        60,
                        null,
                        List.of(new OrderFeeLineDraft(OrderFeeLineKind.BASE_COURSE, null, 499)))))
        .isInstanceOf(InvalidOrderFeeLineException.class)
        .hasMessage("訂正後の請求額が利用ポイントを下回ります。ポイント利用の訂正はポイント機構で行ってください");
  }

  @Test
  @DisplayName("理由と実行者を欠く快照は撥ねられること")
  void snapshotOf_requiresAReasonAndAnActor() {
    Order before = completedOrder();

    // 理由が無いと、凍結済みの記録がなぜ動いたかを後から辿れない。空白だけも「書いていない」と同じ
    assertThatThrownBy(() -> OrderCorrection.snapshotOf(before, null, 7L, AT))
        .isInstanceOf(InvalidOrderCorrectionException.class);
    assertThatThrownBy(() -> OrderCorrection.snapshotOf(before, "   ", 7L, AT))
        .isInstanceOf(InvalidOrderCorrectionException.class);
    // 実行者 null は「利用者が後から削除された」の形。失効した認証セッションの操作と区別できなくなる
    assertThatThrownBy(() -> OrderCorrection.snapshotOf(before, "金額の誤記", null, AT))
        .isInstanceOf(InvalidOrderCorrectionException.class);
  }
}
