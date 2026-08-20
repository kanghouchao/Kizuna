package com.kizuna.shared.exception;

/** 短時間に要求が集中したため受け付けを見送ったことを表す例外。HTTP 429 で応答される。 */
public class TooManyRequestsException extends RuntimeException {

  public TooManyRequestsException(String message) {
    super(message);
  }
}
