package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/** 店舗集合の不変条件（種別と店舗集合の整合）に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidStoreScopeException extends ServiceException {

  public InvalidStoreScopeException(String message) {
    super(message);
  }
}
