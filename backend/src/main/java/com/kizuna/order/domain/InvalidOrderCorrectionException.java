package com.kizuna.order.domain;

import com.kizuna.shared.exception.ServiceException;

/** 完了後訂正の門が対象としない受注（完了していない・取消済み）への訂正要求を表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidOrderCorrectionException extends ServiceException {

  public InvalidOrderCorrectionException(String message) {
    super(message);
  }
}
