package com.kizuna.customer.api.dto;

/**
 * 重複候補の 1 行。JSON キーは Jackson 設定により snake_case（phone_number2 等）。
 *
 * <p>顧客一覧の {@link CustomerSummaryResponse} より項目が多いのは、用途が「絞り込んで選ぶ」ではなく「別人を誤って畳まないための見比べ」だからで、 住所も NG
 * も違えば別人と判る材料になる。統合済みの欄は持たない — 候補は定義上すべて生きた行である。
 */
public record CustomerDuplicateResponse(
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
