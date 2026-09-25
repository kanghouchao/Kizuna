package com.kizuna.customer.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record MergePreferences(String phone, String email, String line) {
  public String selected(ContactType type) {
    return switch (type) {
      case PHONE -> phone;
      case EMAIL -> email;
      case LINE -> line;
    };
  }
}
