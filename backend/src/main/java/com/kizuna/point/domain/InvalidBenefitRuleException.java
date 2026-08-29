package com.kizuna.point.domain;

import com.kizuna.shared.exception.ServiceException;

/** 特典規則の不変条件に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidBenefitRuleException extends ServiceException {

  public InvalidBenefitRuleException(String message) {
    super(message);
  }
}
