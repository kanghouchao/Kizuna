package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/** 緊急昇格の不変条件に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidEmergencyElevationException extends ServiceException {

  public InvalidEmergencyElevationException(String message) {
    super(message);
  }
}
