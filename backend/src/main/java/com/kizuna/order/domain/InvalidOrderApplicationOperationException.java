package com.kizuna.order.domain;

import com.kizuna.shared.exception.ServiceException;

/** 予約申請への不正な操作（終端への再操作・失効後の確定/謝絶・必須項目の欠落）を表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidOrderApplicationOperationException extends ServiceException {

  public InvalidOrderApplicationOperationException(String message) {
    super(message);
  }
}
