package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewCommand;
import java.time.OffsetDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewPermissionRevocationRequest(
    @JsonDeserialize(using = ReviewVersionDeserializer.class) Long version,
    OffsetDateTime withdrawalReceivedAt,
    String reason,
    String dedupeKey) {
  public ReviewCommand.Revoke revoke(String id) {
    return new ReviewCommand.Revoke(id, version, withdrawalReceivedAt, reason, dedupeKey);
  }
}
