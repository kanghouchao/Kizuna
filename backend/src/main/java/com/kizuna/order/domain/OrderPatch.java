package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 注文の部分更新コマンド。null のフィールドは「変更しない」を意味する。
 *
 * <p>合計金額・自動付与ポイントは含まない —
 * 合計は明細の総和として導出され、付与は完了処理だけが確定させる。連絡先の写しも含まない（顧客が着いた受注では撥ねる必要があり、「変更しない」と区別できる 訂正の経路が別に要る）。
 *
 * <p>明細は空リストを「内訳を空にする」として受ける（null だけが「変更しない」）。行に同一性は無く、送られた内容がそのまま新しい内訳になる。
 */
public record OrderPatch(
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    LocalTime arrivalScheduledEndTime,
    Integer pax,
    String courseName,
    Integer courseMinutes,
    Integer extensionMinutes,
    List<OrderFeeLineDraft> feeLines,
    String locationAddress,
    String locationBuilding,
    String carrier,
    String mediaName,
    String remarks,
    String castDriverMessage) {

  /** 会計の場が確定させる部分（コース名の写しと内訳）だけを持つ更新コマンド。位置引数の null 埋めをここ一箇所に閉じ込める。 */
  public static OrderPatch ofAccounting(String courseName, List<OrderFeeLineDraft> feeLines) {
    return new OrderPatch(
        null,
        null,
        null,
        null,
        courseName,
        null,
        null,
        feeLines,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
