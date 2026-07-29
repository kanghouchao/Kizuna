package com.kizuna.shared.exception;

import lombok.NoArgsConstructor;

/** 利用者が是正しうる要求誤りを表す例外基底。status への写像は {@link CommonExceptionHandler} が一手に持つ。 */
@NoArgsConstructor
public class ServiceException extends RuntimeException {

  public ServiceException(String message) {
    super(message);
  }

  public ServiceException(Throwable cause) {
    super(cause);
  }
}
