package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/** 実行者が自分自身のスタッフアカウントを停止しようとしたことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class SelfStopNotAllowedException extends ServiceException {

  public SelfStopNotAllowedException(String message) {
    super(message);
  }
}
