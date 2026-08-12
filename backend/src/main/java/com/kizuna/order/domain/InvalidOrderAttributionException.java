package com.kizuna.order.domain;

import com.kizuna.shared.exception.ServiceException;

/** 受注帰属記録の不変条件に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidOrderAttributionException extends ServiceException {

  public InvalidOrderAttributionException(String message) {
    super(message);
  }
}
