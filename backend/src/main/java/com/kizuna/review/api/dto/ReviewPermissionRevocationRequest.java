package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewChange;
import com.kizuna.review.domain.ReviewValues.Operation;
import java.time.OffsetDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewPermissionRevocationRequest(
    @JsonDeserialize(using = ReviewVersionDeserializer.class) Long version,
    OffsetDateTime withdrawalReceivedAt,
    String reason,
    String dedupeKey) {
  public ReviewChange change(String id) {
    return new ReviewChange(
        id,
        version,
        Operation.PERMISSION_REVOKED,
        reason,
        dedupeKey,
        null,
        withdrawalReceivedAt,
        null);
  }
}
