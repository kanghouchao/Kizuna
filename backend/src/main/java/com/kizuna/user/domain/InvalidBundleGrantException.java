package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/**
 * 能力束授与の不変条件（STAFF は 1 束以上、CAST/MEMBER は束を持たない）に違反したことを表すドメイン例外。 ServiceException 継承により HTTP 400
 * で応答される。
 */
public class InvalidBundleGrantException extends ServiceException {

  public InvalidBundleGrantException(String message) {
    super(message);
  }
}
