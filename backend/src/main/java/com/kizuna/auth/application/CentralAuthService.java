package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.Token;

/** Authentication service interface. */
public interface CentralAuthService {

  /**
   * Logs in a user for Central Authentication.
   *
   * @param username the username
   * @param password the password
   * @return the JWT token
   */
  Token login(String username, String password);

  /**
   * パスワードを変更する。現在のパスワードが一致しない場合は {@link com.kizuna.shared.exception.ServiceException}。
   *
   * @param username 対象ユーザー名
   * @param currentPassword 現在のパスワード（平文）
   * @param newPassword 新しいパスワード（平文）
   */
  void changePassword(String username, String currentPassword, String newPassword);
}
