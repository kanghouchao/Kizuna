package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewCommand;
import com.kizuna.review.domain.ReviewValues.Operation;

public record ReviewDecisionRequest(
    Long version, Decision decision, String reason, String dedupeKey) {
  public enum Decision {
    APPROVE,
    REJECT
  }

  public ReviewCommand.Decide decide(String id) {
    return new ReviewCommand.Decide(
        id,
        version,
        decision == null
            ? null
            : decision == Decision.APPROVE ? Operation.APPROVED : Operation.REJECTED,
        reason,
        dedupeKey);
  }
}
