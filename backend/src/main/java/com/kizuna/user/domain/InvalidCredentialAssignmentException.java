package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/**
 * 資格情報の不変条件（SERVICE は email・パスワードを持たず、他の種別はいずれも必須）に違反したことを表すドメイン例外。 ServiceException 継承により HTTP 400
 * で応答される。
 */
public class InvalidCredentialAssignmentException extends ServiceException {

  public InvalidCredentialAssignmentException(String message) {
    super(message);
  }
}
