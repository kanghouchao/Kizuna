package com.kizuna.order.domain;

import com.kizuna.shared.exception.ServiceException;

/** 顧客が着いた受注の連絡先を訂正しようとしたことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidOrderContactCorrectionException extends ServiceException {

  public InvalidOrderContactCorrectionException(String message) {
    super(message);
  }
}
