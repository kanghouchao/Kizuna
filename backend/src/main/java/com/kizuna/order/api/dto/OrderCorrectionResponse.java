package com.kizuna.order.api.dto;

/**
 * 完了後訂正の結果。門はポイントを動かさないため、動かなかったことと差額を呼び手へ返す。
 *
 * @param previousTotalFee 訂正前の合計（ポイント控除後の請求額）
 * @param totalFee 訂正後の合計
 * @param grantedPoints 完了時に実際に付与したポイント。訂正では動かない時点事実
 * @param recomputedGrantPoints 訂正後の内容で完了していれば付与されたであろうポイント
 * @param grantDifference 上記 2 つの差。0 でなければ手当てが要る
 */
public record OrderCorrectionResponse(
    Integer previousTotalFee,
    Integer totalFee,
    Integer grantedPoints,
    Integer recomputedGrantPoints,
    Integer grantDifference) {}
