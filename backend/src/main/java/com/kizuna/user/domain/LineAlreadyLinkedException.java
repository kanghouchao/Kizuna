package com.kizuna.user.domain;

import com.kizuna.shared.exception.ConflictException;

/** 連携済みの身分に対して重ねて LINE 連携を試みたことを表すドメイン例外。ConflictException 継承により HTTP 409 で応答される。 */
public class LineAlreadyLinkedException extends ConflictException {

  public LineAlreadyLinkedException(String message) {
    super(message);
  }
}
