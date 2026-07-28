package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/** ロールの不変条件（名称必須・権限集合非空）に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidRoleException extends ServiceException {

  public InvalidRoleException(String message) {
    super(message);
  }
}
