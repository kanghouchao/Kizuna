package com.kizuna.user.domain;

import com.kizuna.shared.exception.ConflictException;

/** 授与中のロールを削除しようとしたことを表すドメイン例外。ConflictException 継承により HTTP 409 で応答される。 */
public class RoleInUseException extends ConflictException {

  public RoleInUseException(String message) {
    super(message);
  }
}
