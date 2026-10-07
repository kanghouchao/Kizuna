package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewCorrection;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import java.time.OffsetDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewCorrectionRequest(
    @JsonDeserialize(using = ReviewVersionDeserializer.class) Long version,
    String reason,
    String body,
    String displayName,
    ReceivedVia receivedVia,
    OffsetDateTime receivedAt,
    String originOrderId,
    String dedupeKey) {
  public ReviewCorrection correction(String id) {
    return new ReviewCorrection(
        id,
        version,
        reason,
        new ReviewInput(body, displayName, receivedVia, receivedAt, originOrderId),
        dedupeKey);
  }
}
