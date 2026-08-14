package com.kizuna.order.domain;

import com.kizuna.shared.exception.ServiceException;

/** 受注の取消の不変条件（理由・実行者・時刻の必須）に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidOrderCancellationException extends ServiceException {

  public InvalidOrderCancellationException(String message) {
    super(message);
  }
}
