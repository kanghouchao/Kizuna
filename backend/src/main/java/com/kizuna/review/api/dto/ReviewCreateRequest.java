package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import java.time.OffsetDateTime;

public record ReviewCreateRequest(
    String body,
    String displayName,
    ReceivedVia receivedVia,
    OffsetDateTime receivedAt,
    String originOrderId,
    String dedupeKey) {
  public ReviewInput input() {
    return new ReviewInput(body, displayName, receivedVia, receivedAt, originOrderId);
  }
}
