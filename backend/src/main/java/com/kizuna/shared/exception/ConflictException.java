package com.kizuna.shared.exception;

import java.util.Map;

/** 要求は正しいが、保存済みのデータと両立しないことを表す。応答への写像は CommonExceptionHandler が担う。 */
public class ConflictException extends RuntimeException {
  private final Map<String, String> details;

  public ConflictException(String message) {
    this(message, Map.of());
  }

  public ConflictException(String message, Map<String, String> details) {
    super(message);
    this.details = Map.copyOf(details);
  }

  public Map<String, String> details() {
    return details;
  }
}
