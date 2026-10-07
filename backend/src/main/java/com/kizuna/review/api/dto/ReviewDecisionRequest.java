package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewChange;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.shared.exception.ServiceException;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewDecisionRequest(
    @JsonDeserialize(using = ReviewVersionDeserializer.class) Long version,
    Decision decision,
    String reason,
    String dedupeKey) {
  public enum Decision {
    APPROVE,
    REJECT
  }

  public ReviewChange change(String id) {
    if (decision == null) throw new ServiceException("審査結果を指定してください");
    return new ReviewChange(
        id,
        version,
        decision == Decision.APPROVE ? Operation.APPROVED : Operation.REJECTED,
        reason,
        dedupeKey);
  }
}
