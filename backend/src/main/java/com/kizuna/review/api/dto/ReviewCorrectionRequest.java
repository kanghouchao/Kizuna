package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewCommand;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import java.time.OffsetDateTime;

public record ReviewCorrectionRequest(
    Long version,
    String reason,
    String body,
    String displayName,
    ReceivedVia receivedVia,
    OffsetDateTime receivedAt,
    String originOrderId,
    String dedupeKey) {
  public ReviewCommand.Correct correction(String id) {
    return new ReviewCommand.Correct(
        id,
        version,
        reason,
        new ReviewInput(body, displayName, receivedVia, receivedAt, originOrderId),
        dedupeKey);
  }
}
