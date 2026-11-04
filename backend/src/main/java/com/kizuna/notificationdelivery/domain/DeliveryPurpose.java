package com.kizuna.notificationdelivery.domain;

/** 用途ごとに連絡可否を別に判定し、業務の許可から販促の許可を推定しない。 */
public enum DeliveryPurpose {
  BUSINESS,
  MARKETING
}
