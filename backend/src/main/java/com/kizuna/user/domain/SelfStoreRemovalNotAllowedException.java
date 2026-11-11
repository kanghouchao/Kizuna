package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/** 実行者が自分自身の担当店舗を外そうとしたことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class SelfStoreRemovalNotAllowedException extends ServiceException {

  public SelfStoreRemovalNotAllowedException(String message) {
    super(message);
  }
}
