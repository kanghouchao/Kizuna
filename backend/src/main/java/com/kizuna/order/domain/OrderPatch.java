package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 注文の部分更新コマンド。null は変更なし、明細の空リストは内訳の消去を表す。 合計と付与ポイントは導出するため含めない。連絡先は写し全体を置換する別の操作で扱う。 明細の未変更行は ID
 * で維持し、追加・置換する行だけを採用する。
 */
public record OrderPatch(
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    LocalTime arrivalScheduledEndTime,
    Integer pax,
    List<OrderFeeLineDraft> feeLines,
    String locationAddress,
    String locationBuilding,
    String carrier,
    String mediaName,
    String remarks,
    String castDriverMessage) {

  public static OrderPatch ofAccounting(List<OrderFeeLineDraft> feeLines) {
    return new OrderPatch(null, null, null, null, feeLines, null, null, null, null, null, null);
  }
}
