package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/** スタッフ作成時にメールアドレスが既存ユーザーと重複したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class DuplicateStaffEmailException extends ServiceException {

  public DuplicateStaffEmailException(String message) {
    super(message);
  }
}
