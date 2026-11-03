package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewCommand;
import java.time.OffsetDateTime;

public record ReviewPermissionRevocationRequest(
    Long version, OffsetDateTime withdrawalReceivedAt, String reason, String dedupeKey) {
  public ReviewCommand.Revoke revoke(String id) {
    return new ReviewCommand.Revoke(id, version, withdrawalReceivedAt, reason, dedupeKey);
  }
}
