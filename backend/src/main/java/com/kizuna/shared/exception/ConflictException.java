package com.kizuna.shared.exception;

/** 並行更新の競合（楽観ロック・バージョン不一致）を表す例外基底。status への写像は {@link CommonExceptionHandler} が一手に持つ。 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
