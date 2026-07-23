package com.kizuna.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 並行更新の競合（楽観ロック・バージョン不一致）を表す例外基底。HTTP 409 で応答される。 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
