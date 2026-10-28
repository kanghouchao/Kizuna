package com.kizuna.order.application;

/** 受注に着ける顧客の候補。電話番号は連絡先を読める実行者にだけ載る。 */
public record OrderCustomerCandidate(String id, String name, String phoneNumber) {}
