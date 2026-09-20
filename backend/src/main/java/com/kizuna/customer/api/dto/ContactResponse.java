package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContact;
import java.time.OffsetDateTime;

public record ContactResponse(
    String id,
    String customerId,
    String originCustomerId,
    ContactType type,
    String value,
    boolean preferred,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public static ContactResponse from(CustomerContact c) {
    return new ContactResponse(
        c.getId(),
        c.getCustomerId(),
        c.getOriginCustomerId(),
        c.getType(),
        c.getValue(),
        c.isPreferred(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}
