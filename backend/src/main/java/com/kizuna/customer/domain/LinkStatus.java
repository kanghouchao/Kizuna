package com.kizuna.customer.domain;

/** 会員紐づけの関連状態。ACTIVE は現に有効な 1 区間、RELEASED は解除済みの履歴。 */
public enum LinkStatus {
  ACTIVE,
  RELEASED
}
