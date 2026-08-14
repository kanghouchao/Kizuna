package com.kizuna.order.api.dto;

/**
 * 誤帰属の訂正の進み具合。画面はこれで「あといくら引けるか」を示し、引き残しがあることを店舗に伝える。
 *
 * <p>会員の残高は載せない。この口が到達できる会員は自店舗の顧客と紐づいているとは限らず、店舗へ渡す情報を訂正の 進み具合に閉じておく（ADR 0006 の運用上の前提）。
 *
 * @param grantedPoints その受注がその会員へ与えた付与の合計。訂正の累計がこれを超えることはできない
 * @param correctedPoints 同じ帰属記録に対して既に差し引かれた合計
 */
public record OrderAttributionCorrectionResponse(long grantedPoints, long correctedPoints) {}
