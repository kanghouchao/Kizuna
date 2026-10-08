package com.kizuna.shared.exception;

public class ResourceBusyException extends ServiceUnavailableException {
  public ResourceBusyException(String message) {
    super(message);
  }
}
