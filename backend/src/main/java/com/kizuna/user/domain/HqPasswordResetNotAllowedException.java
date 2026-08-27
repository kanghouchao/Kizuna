package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/**
 * HQ 側ロール保持者へパスワード再設定を試みたことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。
 *
 * <p>実行主体は必ず HQ 側ロールを持つため、自分自身への再設定もこの境界に含まれて拒否される。
 */
public class HqPasswordResetNotAllowedException extends ServiceException {

  public HqPasswordResetNotAllowedException(String message) {
    super(message);
  }
}
