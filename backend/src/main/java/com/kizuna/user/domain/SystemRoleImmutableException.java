package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/** 平台既定ロール（is_system）への変更・削除を拒否したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class SystemRoleImmutableException extends ServiceException {

  public SystemRoleImmutableException(String message) {
    super(message);
  }
}
