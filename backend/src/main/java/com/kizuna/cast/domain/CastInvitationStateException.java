package com.kizuna.cast.domain;

import com.kizuna.shared.exception.ServiceException;

/** 招待の不正な状態遷移・紐づけを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class CastInvitationStateException extends ServiceException {

  public CastInvitationStateException(String message) {
    super(message);
  }
}
