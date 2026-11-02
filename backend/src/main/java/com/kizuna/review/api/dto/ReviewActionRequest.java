package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewCommand;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewActionRequest(
    @JsonDeserialize(using = ReviewVersionDeserializer.class) Long version,
    String reason,
    String dedupeKey) {
  public ReviewCommand.Withdraw withdrawal(String id) {
    return new ReviewCommand.Withdraw(id, version, reason, dedupeKey);
  }
}
