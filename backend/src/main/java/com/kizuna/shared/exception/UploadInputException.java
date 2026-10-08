package com.kizuna.shared.exception;

import lombok.Getter;

@Getter
public class UploadInputException extends ServiceException {
  public enum Reason {
    INVALID,
    LIMIT,
    TYPE
  }

  private final Reason reason;

  public UploadInputException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }
}
