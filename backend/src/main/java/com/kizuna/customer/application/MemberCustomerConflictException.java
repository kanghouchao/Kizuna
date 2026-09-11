package com.kizuna.customer.application;

import com.kizuna.shared.exception.ConflictException;
import org.springframework.modulith.NamedInterface;

@NamedInterface("application")
public class MemberCustomerConflictException extends ConflictException {
  public MemberCustomerConflictException() {
    super("会員の顧客関連が同時に変更されました。もう一度お試しください");
  }
}
