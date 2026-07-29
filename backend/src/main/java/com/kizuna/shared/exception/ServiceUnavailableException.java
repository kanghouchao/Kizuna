package com.kizuna.shared.exception;

/** 一時的に要求を受け付けられないことを表す例外。HTTP 503 で応答される。 */
public class ServiceUnavailableException extends RuntimeException {

  public ServiceUnavailableException(String message) {
    super(message);
  }
}
