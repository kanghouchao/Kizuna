package com.kizuna.order.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.shared.validation.ContactValues;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ContactSnapshot(String name, String phoneNumber, String email, String lineId) {
  public static ContactSnapshot empty() {
    return new ContactSnapshot(null, null, null, null);
  }

  public static ContactSnapshot normalize(
      String name, String phoneNumber, String email, String lineId) {
    return new ContactSnapshot(
        ContactValues.blankToNull(name),
        ContactValues.phone(phoneNumber, "contact_snapshot.phone_number"),
        ContactValues.email(email, "contact_snapshot.email"),
        ContactValues.blankToNull(lineId));
  }
}
