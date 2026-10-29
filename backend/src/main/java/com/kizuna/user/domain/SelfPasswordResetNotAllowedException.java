package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/**
 * 実行主体が自分自身へパスワード再設定を試みたことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。
 *
 * <p>管理者（ROLE_MANAGE 保持者）の代理再設定を拒む守衛には委ねない — JWT の権限は発行時の写しで、ロール降格後も失効まで生き残るため、
 * 降格済みの残存セッションが自分を代理再設定して口座を恒久奪取できてしまう。自己は名指しで拒む。
 */
public class SelfPasswordResetNotAllowedException extends ServiceException {

  public SelfPasswordResetNotAllowedException(String message) {
    super(message);
  }
}
