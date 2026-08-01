package com.kizuna.member.domain;

import com.kizuna.shared.exception.ServiceException;

/** 会員の不変条件（会員コード非空・プラットフォームユーザー ID 必須）に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidMemberException extends ServiceException {

  public InvalidMemberException(String message) {
    super(message);
  }
}
