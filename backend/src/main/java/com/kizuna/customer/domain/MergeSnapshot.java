package com.kizuna.customer.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record MergeSnapshot(
    String id, MergeProfile profile, List<Contact> contacts, List<Link> memberLinks) {
  public record Contact(
      String id,
      String customerId,
      String originCustomerId,
      ContactType type,
      String value,
      boolean preferred,
      boolean deleted,
      ContactPermissionStatus businessStatus,
      ContactPermissionStatus marketingStatus,
      ContactPermissionStatus effectiveBusinessStatus,
      ContactPermissionStatus effectiveMarketingStatus,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    public static Contact from(CustomerContact c, ContactPermissions effective) {
      return new Contact(
          c.getId(),
          c.getCustomerId(),
          c.getOriginCustomerId(),
          c.getType(),
          c.getValue(),
          c.isPreferred(),
          c.isDeleted(),
          c.getBusinessStatus(),
          c.getMarketingStatus(),
          effective.business(),
          effective.marketing(),
          c.getCreatedAt(),
          c.getUpdatedAt());
    }
  }

  public record Link(
      String id,
      String customerId,
      String memberCode,
      LinkStatus status,
      LinkReason reason,
      String operationReason,
      String releaseReason,
      Long linkedBy,
      Long releasedBy,
      OffsetDateTime linkedAt,
      String linkedByName,
      OffsetDateTime releasedAt,
      String releasedByName) {
    public static Link from(CustomerMemberLink c, String linkedName, String releasedName) {
      return new Link(
          c.getId(),
          c.getCustomerId(),
          c.getMemberCode(),
          c.getStatus(),
          c.getReason(),
          c.getOperationReason(),
          c.getReleaseReason(),
          c.getLinkedBy(),
          c.getReleasedBy(),
          c.getLinkedAt(),
          linkedName,
          c.getReleasedAt(),
          releasedName);
    }
  }
}
