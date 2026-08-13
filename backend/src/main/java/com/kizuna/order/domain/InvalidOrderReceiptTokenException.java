package com.kizuna.order.domain;

import com.kizuna.shared.exception.ServiceException;

/** 伝票トークンの不変条件に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidOrderReceiptTokenException extends ServiceException {

  public InvalidOrderReceiptTokenException(String message) {
    super(message);
  }
}
