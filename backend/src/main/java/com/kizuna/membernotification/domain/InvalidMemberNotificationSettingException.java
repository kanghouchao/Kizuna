package com.kizuna.membernotification.domain;

import com.kizuna.shared.exception.ServiceException;

/** 会員の通知設定の不変条件（明示の選択は許可か拒否）に違反したことを表すドメイン例外。HTTP 400 で応答される。 */
public class InvalidMemberNotificationSettingException extends ServiceException {

  public InvalidMemberNotificationSettingException(String message) {
    super(message);
  }
}
