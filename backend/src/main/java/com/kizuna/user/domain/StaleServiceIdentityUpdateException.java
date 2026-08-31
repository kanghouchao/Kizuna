package com.kizuna.user.domain;

import com.kizuna.shared.exception.ConflictException;

/** サービスID授権編集の version 不一致（陳腐化した編集フォームの提出）を表すドメイン例外。ConflictException 継承により HTTP 409 で応答される。 */
public class StaleServiceIdentityUpdateException extends ConflictException {

  public StaleServiceIdentityUpdateException(String message) {
    super(message);
  }
}
