package com.kizuna.customer.domain;

import com.kizuna.shared.exception.ServiceException;

/** 会員紐づけの不変条件に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidCustomerMemberLinkException extends ServiceException {

  public InvalidCustomerMemberLinkException(String message) {
    super(message);
  }
}
