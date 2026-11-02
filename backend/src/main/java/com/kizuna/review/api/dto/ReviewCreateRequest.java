package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewCommand;
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
  public ReviewCommand.Receive receipt() {
    return new ReviewCommand.Receive(
        new ReviewInput(body, displayName, receivedVia, receivedAt, originOrderId), dedupeKey);
  }
}
