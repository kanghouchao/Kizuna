package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewChange;
import com.kizuna.review.domain.ReviewValues.Operation;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewActionRequest(
    @JsonDeserialize(using = ReviewVersionDeserializer.class) Long version,
    String reason,
    String dedupeKey) {
  public ReviewChange withdrawal(String id) {
    return new ReviewChange(id, version, Operation.WITHDRAWN, reason, dedupeKey);
  }
}
