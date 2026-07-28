package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/**
 * ロール授与の不変条件（STAFF は 1 ロール以上、CAST/MEMBER はロールを持たない）に違反したことを表すドメイン例外。 ServiceException 継承により HTTP
 * 400 で応答される。
 */
public class InvalidRoleGrantException extends ServiceException {

  public InvalidRoleGrantException(String message) {
    super(message);
  }
}
