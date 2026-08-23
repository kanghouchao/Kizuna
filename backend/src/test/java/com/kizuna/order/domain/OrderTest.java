package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

  private Order orderWithStatus(OrderStatus status) {
    return Order.builder().status(status).build();
  }

  @Test
  @DisplayName("確認済みの注文を完了すると利用ポイントが減算の明細になり、合計がそのぶん下がること")
  void completeWith_fromConfirmed() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.SURCHARGE, "指名料", 12000)));

    order.completeWith(500, 120);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getTotalFee()).isEqualTo(11500);
    assertThat(order.getAutoGrantPoints()).isEqualTo(120);
    assertThat(order.getFeeLines())
        .extracting(OrderFeeLine::getKind, OrderFeeLine::getAmount)
        .containsExactly(
            tuple(OrderFeeLineKind.SURCHARGE, 12000),
            tuple(OrderFeeLineKind.POINT_REDEMPTION, -500));
  }

  @Test
  @DisplayName("キャンセル済みの注文は完了できないこと")
  void completeWith_fromCancelled_isRejected() {
    Order order = orderWithStatus(OrderStatus.CANCELLED);
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.SURCHARGE, "指名料", 12000)));

    assertThatThrownBy(() -> order.completeWith(0, 120))
        .isInstanceOf(IllegalOrderStateTransitionException.class)
        .hasMessageContaining("CANCELLED")
        .hasMessageContaining("COMPLETED");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getTotalFee()).as("撥ねた完了は合計を動かさないこと").isEqualTo(12000);
  }

  @Test
  @DisplayName("完了済みの注文を再度完了できず、確定済みの内訳も上書きされないこと")
  void completeWith_isNotIdempotent() {
    // 完了は台帳記帳と不可分のため、同一状態への静默冪等（transitionTo）に委ねると二重記帳になる
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.SURCHARGE, "指名料", 12000)));
    order.completeWith(500, 120);

    assertThatThrownBy(() -> order.completeWith(100, 99))
        .isInstanceOf(IllegalOrderStateTransitionException.class)
        .hasMessageContaining("COMPLETED");
    assertThat(order.getTotalFee()).isEqualTo(11500);
    assertThat(order.getFeeLines()).hasSize(2);
    assertThat(order.getAutoGrantPoints()).isEqualTo(120);
  }

  @Test
  @DisplayName("合計は明細の帯符号金額の単純総和であること")
  void totalFee_isSumOfFeeLines() {
    Order order = Order.builder().status(OrderStatus.CONFIRMED).courseName("60 分コース").build();

    order.replaceStoreFeeLines(
        List.of(
            draft(OrderFeeLineKind.BASE_COURSE, null, 14000),
            draft(OrderFeeLineKind.EXTENSION, "30 分延長", 6000),
            draft(OrderFeeLineKind.OPTION, "オプション A", 2000),
            draft(OrderFeeLineKind.DISCOUNT, "初回割", -3000),
            draft(OrderFeeLineKind.MANUAL_ADJUST, "端数調整", -500)));

    assertThat(order.getTotalFee()).isEqualTo(18500);
  }

  @Test
  @DisplayName("明細の差し替えは前の内容を残さず、合計を取り直すこと")
  void replaceStoreFeeLines_replacesWholeBreakdown() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.OPTION, "オプション A", 2000)));

    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.OPTION, "オプション B", 3000)));

    assertThat(order.getFeeLines()).extracting(OrderFeeLine::getName).containsExactly("オプション B");
    assertThat(order.getTotalFee()).isEqualTo(3000);

    order.replaceStoreFeeLines(List.of());
    assertThat(order.getFeeLines()).isEmpty();
    assertThat(order.getTotalFee()).as("内訳が空なら合計も 0 であること").isZero();
  }

  @Test
  @DisplayName("店舗の明細差し替えはポイント利用の行を作れず、既にある行も消せないこと")
  void replaceStoreFeeLines_cannotTouchSystemOwnedLines() {
    // 台帳の減算仕訳と対で書かれた記録が通常の編集で外れると、内訳と台帳が黙って食い違う
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.SURCHARGE, "指名料", 10000)));
    order.completeWith(500, 0);

    assertThatThrownBy(
            () ->
                order.replaceStoreFeeLines(
                    List.of(draft(OrderFeeLineKind.POINT_REDEMPTION, "ポイント利用", -300))))
        .isInstanceOf(InvalidOrderFeeLineException.class);

    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.SURCHARGE, "指名料", 8000)));
    assertThat(order.getFeeLines())
        .extracting(OrderFeeLine::getKind, OrderFeeLine::getAmount)
        .containsExactlyInAnyOrder(
            tuple(OrderFeeLineKind.SURCHARGE, 8000),
            tuple(OrderFeeLineKind.POINT_REDEMPTION, -500));
    assertThat(order.getTotalFee()).isEqualTo(7500);
  }

  @Test
  @DisplayName("種別の符号約定に反する金額は撥ねられること")
  void feeLine_signIsEnforcedPerKind() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);

    assertThatThrownBy(
            () -> order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.OPTION, "オプション", -1))))
        .isInstanceOf(InvalidOrderFeeLineException.class);
    assertThatThrownBy(
            () -> order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.DISCOUNT, "割引", 1))))
        .isInstanceOf(InvalidOrderFeeLineException.class);
    assertThat(order.getFeeLines()).as("撥ねた差し替えは内訳を動かさないこと").isEmpty();

    // 手動調整だけが符号を縛られない（合計を機械和から外す唯一の口）
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.MANUAL_ADJUST, "調整", -1200)));
    assertThat(order.getTotalFee()).isEqualTo(-1200);
  }

  @Test
  @DisplayName("基本コース料金の行名称はコース名の写しから採り、コース名が無ければ撥ねること")
  void baseCourseLine_takesItsNameFromTheCourseSnapshot() {
    Order withoutCourseName = orderWithStatus(OrderStatus.CONFIRMED);
    assertThatThrownBy(
            () ->
                withoutCourseName.replaceStoreFeeLines(
                    List.of(draft(OrderFeeLineKind.BASE_COURSE, "行から名乗ろうとした名前", 14000))))
        .isInstanceOf(InvalidOrderFeeLineException.class);

    Order order = Order.builder().status(OrderStatus.CONFIRMED).courseName("90 分コース").build();
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.BASE_COURSE, "行から名乗ろうとした名前", 14000)));

    assertThat(order.getFeeLines()).extracting(OrderFeeLine::getName).containsExactly("90 分コース");
  }

  @Test
  @DisplayName("明細は読み手が直接書き換えられないこと")
  void getFeeLines_isNotWritable() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.OPTION, "オプション", 2000)));

    List<OrderFeeLine> lines = order.getFeeLines();
    assertThatThrownBy(() -> lines.add(OrderFeeLine.of(OrderFeeLineKind.OPTION, "横入り", 9999)))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(order.getTotalFee()).isEqualTo(2000);
  }

  @Test
  @DisplayName("同じ要求で送られたコース名が基本コース料金の行名称に載ること")
  void apply_writesCourseNameBeforeFeeLines() {
    // 順序が逆だと、コースを変えた同じ更新で行だけが古いコース名を名乗る
    Order order = Order.builder().status(OrderStatus.CONFIRMED).courseName("60 分コース").build();

    order.apply(
        OrderPatch.ofAccounting(
            "120 分コース", List.of(draft(OrderFeeLineKind.BASE_COURSE, null, 22000))));

    assertThat(order.getCourseName()).isEqualTo("120 分コース");
    assertThat(order.getFeeLines()).extracting(OrderFeeLine::getName).containsExactly("120 分コース");
  }

  @Test
  @DisplayName("コース名だけを直した更新でも基本コース料金の行名称が追随すること")
  void apply_courseNameAloneStillRenamesTheBaseCourseLine() {
    // 明細を伴わない更新で写しが取り残されると、同じ受注が二つのコース名を主張する
    Order order = Order.builder().status(OrderStatus.CONFIRMED).courseName("60 分コース").build();
    order.replaceStoreFeeLines(
        List.of(
            draft(OrderFeeLineKind.BASE_COURSE, null, 14000),
            draft(OrderFeeLineKind.OPTION, "オプション A", 2000)));

    order.apply(OrderPatch.ofAccounting("120 分コース", null));

    assertThat(order.getCourseName()).isEqualTo("120 分コース");
    assertThat(order.getFeeLines())
        .extracting(OrderFeeLine::getKind, OrderFeeLine::getName)
        .containsExactly(
            tuple(OrderFeeLineKind.BASE_COURSE, "120 分コース"),
            tuple(OrderFeeLineKind.OPTION, "オプション A"));
    assertThat(order.getTotalFee()).as("名称の追随は合計を動かさないこと").isEqualTo(16000);
  }

  @Test
  @DisplayName("基本コース料金の行がある受注はコース名を空にできないこと")
  void apply_cannotBlankTheCourseNameWhileABaseCourseLineExists() {
    Order order = Order.builder().status(OrderStatus.CONFIRMED).courseName("60 分コース").build();
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.BASE_COURSE, null, 14000)));

    assertThatThrownBy(() -> order.apply(OrderPatch.ofAccounting("  ", null)))
        .isInstanceOf(InvalidOrderFeeLineException.class);
    assertThat(order.getCourseName()).as("撥ねた更新は写しを動かさないこと").isEqualTo("60 分コース");
  }

  private static OrderFeeLineDraft draft(OrderFeeLineKind kind, String name, int amount) {
    return new OrderFeeLineDraft(kind, name, amount);
  }

  @Test
  @DisplayName("確定済みの注文を理由付きで取消でき、理由・実行者・時刻が残ること")
  void cancelWith_fromConfirmed_recordsReasonActorAndTime() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    OffsetDateTime at = OffsetDateTime.parse("2026-08-14T17:42:00+09:00");

    order.cancelWith("客都合。当日夕方に体調不良の連絡あり", 7L, at);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getCancelledReason()).isEqualTo("客都合。当日夕方に体調不良の連絡あり");
    assertThat(order.getCancelledBy()).isEqualTo(7L);
    assertThat(order.getCancelledAt()).isEqualTo(at);
  }

  @Test
  @DisplayName("二度目の取消は撥ねられ、初回の理由と実行者が上書きされないこと")
  void cancelWith_secondTime_isRejectedAndFirstRecordKept() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    OffsetDateTime first = OffsetDateTime.parse("2026-08-14T17:42:00+09:00");
    order.cancelWith("初回の理由", 7L, first);

    assertThatThrownBy(() -> order.cancelWith("二度目の理由", 8L, first.plusHours(1)))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThat(order.getCancelledReason()).isEqualTo("初回の理由");
    assertThat(order.getCancelledBy()).isEqualTo(7L);
    assertThat(order.getCancelledAt()).isEqualTo(first);
  }

  @Test
  @DisplayName("完了済みの注文は専用取消の経路では取り消せないこと")
  void cancelWith_outsideConfirmed_isRejected() {
    // 定義域は CONFIRMED → CANCELLED のみ。未処理の予約申請は申請側の謝絶が受け持ち、
    // 誤完了の救済経路はまだ存在しない（ADR 0013）
    Order order = orderWithStatus(OrderStatus.COMPLETED);
    assertThatThrownBy(() -> order.cancelWith("理由", 7L, OffsetDateTime.now()))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThat(order.getStatus()).as("拒否時に状態が変わらないこと").isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getCancelledReason()).as("拒否時に理由が書かれないこと").isNull();
  }

  @Test
  @DisplayName("理由・実行者・時刻のいずれかを欠く取消は撥ねられること")
  void cancelWith_requiresReasonActorAndTime() {
    OffsetDateTime at = OffsetDateTime.now();
    assertThatThrownBy(() -> orderWithStatus(OrderStatus.CONFIRMED).cancelWith(null, 7L, at))
        .isInstanceOf(InvalidOrderCancellationException.class);
    assertThatThrownBy(() -> orderWithStatus(OrderStatus.CONFIRMED).cancelWith("  ", 7L, at))
        .isInstanceOf(InvalidOrderCancellationException.class);
    assertThatThrownBy(() -> orderWithStatus(OrderStatus.CONFIRMED).cancelWith("理由", null, at))
        .isInstanceOf(InvalidOrderCancellationException.class);
    assertThatThrownBy(() -> orderWithStatus(OrderStatus.CONFIRMED).cancelWith("理由", 7L, null))
        .isInstanceOf(InvalidOrderCancellationException.class);
  }

  @Test
  @DisplayName("キャンセル済みの注文からは一切遷移できないこと")
  void transitions_fromCancelled_areRejected() {
    Order order = orderWithStatus(OrderStatus.CANCELLED);
    assertThatThrownBy(() -> order.transitionTo(OrderStatus.CONFIRMED))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
    assertThatThrownBy(() -> order.transitionTo(OrderStatus.COMPLETED))
        .isInstanceOf(IllegalOrderStateTransitionException.class);
  }

  @Test
  @DisplayName("部分更新で人数を変更でき、null は変更しないこと")
  void apply_pax() {
    Order order = Order.builder().status(OrderStatus.CONFIRMED).pax(2).build();

    order.apply(patchWithPax(5));
    assertThat(order.getPax()).isEqualTo(5);

    order.apply(patchWithPax(null));
    assertThat(order.getPax()).as("null の人数は変更しないこと").isEqualTo(5);
  }

  private OrderPatch patchWithPax(Integer pax) {
    return new OrderPatch(
        null, null, null, pax, null, null, null, null, null, null, null, null, null, null);
  }

  @Test
  @DisplayName("部分更新で営業日・場所・媒体を変更できること")
  void apply_businessDateLocationAndMedia() {
    // 改期（営業日の変更）を編集で行えないと、取消して再登録する運用になり取消の記録が雑音で汚れる
    Order order =
        Order.builder()
            .status(OrderStatus.CONFIRMED)
            .businessDate(LocalDate.parse("2026-08-15"))
            .locationAddress("誤記の住所")
            .build();

    order.apply(
        new OrderPatch(
            LocalDate.parse("2026-08-20"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "中央区銀座 1-2-3",
            "グランドホテル 1204",
            "ドコモ",
            "自社サイト",
            null,
            null));

    assertThat(order.getBusinessDate()).isEqualTo(LocalDate.parse("2026-08-20"));
    assertThat(order.getLocationAddress()).isEqualTo("中央区銀座 1-2-3");
    assertThat(order.getLocationBuilding()).isEqualTo("グランドホテル 1204");
    assertThat(order.getCarrier()).isEqualTo("ドコモ");
    assertThat(order.getMediaName()).isEqualTo("自社サイト");
  }

  @Test
  @DisplayName("顧客の着いていない受注の連絡先を訂正でき、null は変更しないこと")
  void correctContact_onUnlinkedOrder() {
    Order order = Order.builder().contactName("誤記の名前").contactPhoneNumber("09011112222").build();

    order.correctContact("正しい名前", null);

    assertThat(order.getContactName()).isEqualTo("正しい名前");
    assertThat(order.getContactPhoneNumber()).as("null の項目は変更しないこと").isEqualTo("09011112222");
  }

  @Test
  @DisplayName("顧客の着いた受注の連絡先訂正は撥ねられること（黙って捨てない）")
  void correctContact_onLinkedOrder_isRejected() {
    // 着いていれば名乗りの正本は台帳の行。黙って捨てると送り手は直ったと誤解したまま誤記が残る
    Order linked = Order.builder().customerId("c1").build();

    assertThatThrownBy(() -> linked.correctContact("受注側から書こうとした名前", "09099998888"))
        .isInstanceOf(InvalidOrderContactCorrectionException.class);
    assertThat(linked.getContactName()).isNull();
    assertThat(linked.getContactPhoneNumber()).isNull();
  }

  @Test
  @DisplayName("終端状態は完了・取消の 2 つで、確定は終端でないこと")
  void isTerminal_coversCompletedAndCancelled() {
    assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
    assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
    assertThat(OrderStatus.CONFIRMED.isTerminal()).isFalse();
  }

  @Test
  @DisplayName("連絡先の写しは顧客が着いていない受注にだけ入ること")
  void recordContactIfUnlinked_onlyWhenNoCustomer() {
    Order unlinked = Order.builder().build();

    unlinked.recordContactIfUnlinked("重複照合の来客", "09012345678");
    assertThat(unlinked.getContactName()).isEqualTo("重複照合の来客");
    assertThat(unlinked.getContactPhoneNumber()).isEqualTo("09012345678");

    // 台帳の行が名乗りを持つ受注に写しを重ねると、どちらが正本かが読み手から消える
    Order linked = Order.builder().customerId("c1").build();

    linked.recordContactIfUnlinked("重複照合の来客", "09012345678");
    assertThat(linked.getContactName()).isNull();
    assertThat(linked.getContactPhoneNumber()).isNull();
  }

  @Test
  @DisplayName("同じステータスへの遷移は何もしない（冪等）こと")
  void transitionTo_sameStatus_isNoOp() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.transitionTo(OrderStatus.CONFIRMED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  @DisplayName("完了後の訂正は明細・実績時刻・コース快照を直し、合計を取り直すこと")
  void correct_rewritesTheCorrectableSetAndRederivesTheTotal() {
    Order order = Order.builder().status(OrderStatus.CONFIRMED).courseName("60 分コース").build();
    order.replaceStoreFeeLines(
        List.of(
            draft(OrderFeeLineKind.BASE_COURSE, null, 14000),
            draft(OrderFeeLineKind.OPTION, "オプション A", 2000)));
    order.completeWith(500, 120);

    order.correct(
        new OrderCorrectionCommand(
            LocalTime.of(20, 15),
            LocalTime.of(22, 40),
            "120 分コース",
            120,
            30,
            List.of(
                draft(OrderFeeLineKind.BASE_COURSE, null, 22000),
                draft(OrderFeeLineKind.OPTION, "オプション B", 3000))));

    assertThat(order.getStatus()).as("訂正は状態を戻さないこと").isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getActualArrivalTime()).isEqualTo(LocalTime.of(20, 15));
    assertThat(order.getActualEndTime()).isEqualTo(LocalTime.of(22, 40));
    assertThat(order.getCourseName()).isEqualTo("120 分コース");
    assertThat(order.getCourseMinutes()).isEqualTo(120);
    assertThat(order.getExtensionMinutes()).isEqualTo(30);
    // 金額行と対で直せるので、行の名称も新しいコース名を名乗る（半修状態を作らない）
    assertThat(order.getFeeLines())
        .extracting(OrderFeeLine::getKind, OrderFeeLine::getName, OrderFeeLine::getAmount)
        .containsExactly(
            tuple(OrderFeeLineKind.POINT_REDEMPTION, "ポイント利用", -500),
            tuple(OrderFeeLineKind.BASE_COURSE, "120 分コース", 22000),
            tuple(OrderFeeLineKind.OPTION, "オプション B", 3000));
    assertThat(order.getTotalFee()).isEqualTo(24500);
    assertThat(order.getAutoGrantPoints()).as("門はポイントを一切動かさないこと").isEqualTo(120);
  }

  @Test
  @DisplayName("完了後の訂正はポイント利用の行を残し、要求に混ぜられても撥ねること")
  void correct_cannotTouchSystemOwnedLines() {
    Order order = orderWithStatus(OrderStatus.CONFIRMED);
    order.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.SURCHARGE, "指名料", 10000)));
    order.completeWith(500, 0);

    assertThatThrownBy(
            () ->
                order.correct(
                    command(List.of(draft(OrderFeeLineKind.POINT_REDEMPTION, "ポイント利用", -300)))))
        .isInstanceOf(InvalidOrderFeeLineException.class);

    order.correct(command(List.of(draft(OrderFeeLineKind.SURCHARGE, "指名料", 8000))));
    assertThat(order.getFeeLines())
        .extracting(OrderFeeLine::getKind, OrderFeeLine::getAmount)
        .containsExactly(
            tuple(OrderFeeLineKind.POINT_REDEMPTION, -500),
            tuple(OrderFeeLineKind.SURCHARGE, 8000));
    assertThat(order.getTotalFee()).isEqualTo(7500);
  }

  @Test
  @DisplayName("完了していない受注は訂正できないこと")
  void correct_rejectsNonCompletedOrders() {
    Order confirmed = orderWithStatus(OrderStatus.CONFIRMED);
    confirmed.replaceStoreFeeLines(List.of(draft(OrderFeeLineKind.OPTION, "オプション", 2000)));

    assertThatThrownBy(() -> confirmed.correct(command(List.of())))
        .isInstanceOf(InvalidOrderCorrectionException.class);
    assertThat(confirmed.getTotalFee()).as("撥ねた訂正は内訳を動かさないこと").isEqualTo(2000);

    // 誤取消の救済は同内容で受注を起こし直すこと（取消理由と実行者の保護を訂正口で迂回させない）
    Order cancelled = orderWithStatus(OrderStatus.CONFIRMED);
    cancelled.cancelWith("誤登録", 1L, OffsetDateTime.now());

    assertThatThrownBy(() -> cancelled.correct(command(List.of())))
        .isInstanceOf(InvalidOrderCorrectionException.class);
    assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  private OrderCorrectionCommand command(List<OrderFeeLineDraft> feeLines) {
    return new OrderCorrectionCommand(null, null, null, null, null, feeLines);
  }
}
