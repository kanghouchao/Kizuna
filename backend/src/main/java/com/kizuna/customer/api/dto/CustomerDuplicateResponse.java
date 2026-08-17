package com.kizuna.customer.api.dto;

/**
 * 重複候補の 1 行。JSON キーは Jackson 設定により snake_case（phone_number2 等）。
 *
 * <p>顧客一覧の {@link CustomerSummaryResponse}
 * より項目が多いのは、この読み口の用途が「絞り込んで選ぶ」ではなく「別人を誤って畳まないための見比べ」だからである。 住所も NG
 * も、それが違えば同一人物ではないと判る材料であり、この画面では省ける項目ではない。
 *
 * <p>受注件数と会員紐づけの有無は顧客行そのものが持たない事実で、application 層が別の問い合わせから補う。
 *
 * <p>統合済みかどうかの欄は持たない。候補は定義上すべて生きた行なので、常に同じ値になる欄は載せない。
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
