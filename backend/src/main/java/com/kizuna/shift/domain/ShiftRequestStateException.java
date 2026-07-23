package com.kizuna.shift.domain;

import com.kizuna.shared.exception.ServiceException;

/** 出勤希望の不正な状態遷移を表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class ShiftRequestStateException extends ServiceException {

  public ShiftRequestStateException(String message) {
    super(message);
  }
}
