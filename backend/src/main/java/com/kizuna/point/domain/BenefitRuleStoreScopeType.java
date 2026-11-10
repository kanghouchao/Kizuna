package com.kizuna.point.domain;

/** 特典規則の適用店舗の種別。発火側の絞り込み（どの店舗での事象を拾うか）であって、授権の店舗集合ではない。 */
public enum BenefitRuleStoreScopeType {
  ALL_STORES,
  SPECIFIC_STORES
}
