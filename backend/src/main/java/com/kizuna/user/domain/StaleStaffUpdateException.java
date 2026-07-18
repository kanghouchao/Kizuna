package com.kizuna.user.domain;

import com.kizuna.shared.exception.ConflictException;

/** スタッフ授権編集の version 不一致（陳腐化した編集フォームの提出）を表すドメイン例外。ConflictException 継承により HTTP 409 で応答される（#400）。 */
public class StaleStaffUpdateException extends ConflictException {

  public StaleStaffUpdateException(String message) {
    super(message);
  }
}
