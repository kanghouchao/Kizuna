package com.kizuna.point.domain;

import com.kizuna.shared.exception.ServiceException;

/** ポイント台帳への書き込みが不変条件に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidPointEntryException extends ServiceException {

  public InvalidPointEntryException(String message) {
    super(message);
  }
}
