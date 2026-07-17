package com.kizuna.user.domain;

import com.kizuna.shared.exception.ServiceException;

/** 能力束の不変条件（名称必須・能力集合非空）に違反したことを表すドメイン例外。ServiceException 継承により HTTP 400 で応答される。 */
public class InvalidCapabilityBundleException extends ServiceException {

  public InvalidCapabilityBundleException(String message) {
    super(message);
  }
}
