package com.kizuna.shared.exception;

import java.util.Map;

/** 並行更新の競合を表す。応答への写像は CommonExceptionHandler が担う。 */
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
