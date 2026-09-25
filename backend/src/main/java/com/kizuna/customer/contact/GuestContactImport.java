package com.kizuna.customer.contact;

import com.kizuna.customer.domain.ContactType;

public record GuestContactImport(
    ContactType type, String value, MarketingResult marketingResult, String contactId) {
  public enum MarketingResult {
    NOT_REQUESTED,
    ALLOWED,
    DENIED_PRESERVED
  }
}
