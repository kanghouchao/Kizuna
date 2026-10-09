package com.kizuna.reporting.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdvertisingAmounts(
    String status,
    Long entryCount,
    Long salesAmount,
    Long recruitmentAmount,
    Long recordedTotalAmount) {

  public static String inapplicableStatus(ReportCriteria criteria) {
    if (criteria.from().getDayOfMonth() != 1
        || criteria.to().getDayOfMonth() != criteria.to().lengthOfMonth())
      return "NOT_APPLICABLE_PARTIAL_MONTH";
    return criteria.groupBy().equals("day") ? "NOT_APPLICABLE_DAY_GROUPING" : null;
  }

  public String explanation() {
    return switch (status) {
      case "NOT_APPLICABLE_PARTIAL_MONTH" -> "広告費は月初から月末までの整月範囲のみ対象です。";
      case "NOT_APPLICABLE_DAY_GROUPING" -> "月単位の広告費は日別集計の対象外です。月別または店舗別を選択してください。";
      default -> "広告費は登録済み有効行の合計です。登録なし・登録額0は実費0や入力完了を意味しません。";
    };
  }
}
