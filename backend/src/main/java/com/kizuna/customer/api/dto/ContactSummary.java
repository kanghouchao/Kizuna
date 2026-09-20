package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContact;

public record ContactSummary(String id, ContactType type, String value) {
  public static ContactSummary from(CustomerContact c) {
    return new ContactSummary(c.getId(), c.getType(), c.getValue());
  }
}
