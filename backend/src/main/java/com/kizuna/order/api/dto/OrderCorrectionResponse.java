package com.kizuna.order.api.dto;

/**
 * 完了後訂正の結果。門はポイントを動かさないので、動いたのは会計金額だけであることを前後の額で示す。
 *
 * <p>付与の差額は載せない。手当ては別機構（手動調整）が担い、その調整は受注にも帰属記録にも結び付かないため、
 * 門は「前回の助言が実行されたか」を知る手立てを持たない。差額を可執行の指示として返すと、二度目の訂正が一度目の 手当てを勘定に入れないまま次の額を勧める。要否と額の判断は台帳側の画面に委ねる。
 *
 * @param previousTotalFee 訂正前の合計（ポイント控除後の請求額）
 * @param totalFee 訂正後の合計
 */
public record OrderCorrectionResponse(Integer previousTotalFee, Integer totalFee) {}
