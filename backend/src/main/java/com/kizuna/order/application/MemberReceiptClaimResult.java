package com.kizuna.order.application;

/** 伝票トークンの申領で台帳へ記帳したポイントの合計。 */
public record MemberReceiptClaimResult(long grantedPoints) {}
