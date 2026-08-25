package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/**
 * 委譲の境界（ADR 0020 の守衛 G3）を越えるアカウントを編集・停止しようとしたことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。
 *
 * <p>対象は一覧には現れる（同僚として見える必要がある）ので、不在を表す 404 では代替できない。
 */
public class StaffOutOfDelegationScopeException extends ServiceException {

  public StaffOutOfDelegationScopeException(String message) {
    super(message);
  }
}
