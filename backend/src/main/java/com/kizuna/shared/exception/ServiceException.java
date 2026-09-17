package com.kizuna.shared.exception;

import java.util.Map;
import lombok.NoArgsConstructor;

/** 利用者が是正しうる要求誤りを表す例外基底。status への写像は {@link CommonExceptionHandler} が一手に持つ。 */
@NoArgsConstructor
public class ServiceException extends RuntimeException {

  private Map<String, String> details = Map.of();

  public Map<String, String> details() {
    return details;
  }

  public ServiceException(String message, Map<String, String> details) {
    super(message);
    this.details = Map.copyOf(details);
  }

  public ServiceException(String message) {
    super(message);
  }

  public ServiceException(Throwable cause) {
    super(cause);
  }
}
