package com.kizuna.customer.api.dto;

import com.kizuna.customer.application.CustomerViews.ContactView;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import java.time.OffsetDateTime;

public record ContactResponse(
    String id,
    String customerId,
    String originCustomerId,
    ContactType type,
    String value,
    boolean preferred,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    ContactPermissionStatus businessStatus,
    ContactPermissionStatus marketingStatus,
    ContactPermissionStatus effectiveBusinessStatus,
    ContactPermissionStatus effectiveMarketingStatus) {
  public static ContactResponse of(ContactView view) {
    var c = view.contact();
    var effective = view.effective();
    return new ContactResponse(
        c.getId(),
        c.getCustomerId(),
        c.getOriginCustomerId(),
        c.getType(),
        c.getValue(),
        c.isPreferred(),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        c.getBusinessStatus(),
        c.getMarketingStatus(),
        effective.business(),
        effective.marketing());
  }
}
