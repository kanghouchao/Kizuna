package com.kizuna.order.domain;

import com.kizuna.shared.exception.ServiceException;

/** 受注明細の不変条件（種別ごとの符号・名称の必須・システム専有種別）に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidOrderFeeLineException extends ServiceException {

  public InvalidOrderFeeLineException(String message) {
    super(message);
  }
}
