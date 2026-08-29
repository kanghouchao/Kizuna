package com.kizuna.customer.api.dto;

/**
 * 統合の前に見比べる 1 行。重複候補のグループと、顧客一覧から選んだ任意の 2 行の両方がこの型で返る（一覧から選んだ 2 行は重複候補とは限らない）。JSON キーは snake_case。
 *
 * <p>顧客一覧の {@link CustomerSummaryResponse}
 * より項目が多いのは、用途が「絞り込んで選ぶ」ではなく「別人を誤って畳まないための見比べ」だからである。統合済みの欄は持たない — 見比べる対象は定義上すべて生きた行である。
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
    String lineId,
    String usageAreas,
    String ngType,
    String ngContent,
    boolean memberLinked,
    long orderCount) {}
