package com.kizuna.point.domain;

import com.kizuna.shared.exception.ConflictException;

/** 特典規則編集の version 不一致（陳腐化した編集フォームの提出）を表すドメイン例外。ConflictException 継承により HTTP 409 で応答される。 */
public class StaleBenefitRuleUpdateException extends ConflictException {

  public StaleBenefitRuleUpdateException(String message) {
    super(message);
  }
}
