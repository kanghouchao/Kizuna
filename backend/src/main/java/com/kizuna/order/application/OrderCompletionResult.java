package com.kizuna.order.application;

/** 会員に達しない完了でだけ伝票トークンの生値を持つ。生値は保存されず、この結果の外へ出る経路は無い。 */
public record OrderCompletionResult(String receiptToken) {}
