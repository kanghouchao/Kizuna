package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/**
 * 管理者（ROLE_MANAGE 保持者）へパスワード再設定を試みたことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。
 * 管理者の口座は代理では奪えない — 本人の再設定だけが経路である。
 */
public class HqPasswordResetNotAllowedException extends ServiceException {

  public HqPasswordResetNotAllowedException(String message) {
    super(message);
  }
}
