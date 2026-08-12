package com.kizuna.point.api.dto;

/**
 * 会員本人に返す現在のポイント残高（JSON キーは snake_case）。
 *
 * <p>残高は期限内ロットの残りの合計であって、明細に並ぶ増減の総和ではない — 期限切れは仕訳を積まずに残高だけを減らすため、 画面で明細を足し上げても一致しない。
 *
 * <p>1 件の仕訳は int でも、台帳全体の合計は int を超えうる。
 */
public record MemberPointBalanceResponse(long balance) {}
