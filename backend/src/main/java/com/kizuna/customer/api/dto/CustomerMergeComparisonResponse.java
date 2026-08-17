package com.kizuna.customer.api.dto;

/**
 * 統合の前に見比べる 1 行。JSON キーは Jackson 設定により snake_case（phone_number2 等）。
 *
 * <p>重複候補のグループと、顧客一覧から選んだ任意の 2 行の両方がこの型で返る。名前が「重複候補の行」でないのは、 一覧から選んだ 2 行が重複候補とは限らないためである —
 * 候補の提示は手がかりで、統合の実行はそれとは独立した人手の操作である（ADR 0010）。
 *
 * <p>顧客一覧の {@link CustomerSummaryResponse} より項目が多いのは、用途が「絞り込んで選ぶ」ではなく「別人を誤って畳まないための見比べ」だからで、 住所も NG
 * も違えば別人と判る材料になる。統合済みの欄は持たない — 見比べる対象は定義上すべて生きた行である。
 */
public record CustomerMergeComparisonResponse(
    String id,
    String name,
    String phoneNumber,
    String phoneNumber2,
    String address,
    String buildingName,
    String classification,
    Boolean hasPet,
    String rank,
    String lineId,
    String usageAreas,
    String ngType,
    String ngContent,
    boolean memberLinked,
    long orderCount) {}
