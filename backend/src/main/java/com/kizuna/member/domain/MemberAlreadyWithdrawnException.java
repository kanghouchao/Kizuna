package com.kizuna.member.domain;

import com.kizuna.shared.exception.ConflictException;

/** 退会済みの会員への再退会。終端状態の繰り返しは入力の誤りではなく競合として 409 で返す。 */
public class MemberAlreadyWithdrawnException extends ConflictException {

  public MemberAlreadyWithdrawnException() {
    super("この会員は既に退会しています");
  }
}
