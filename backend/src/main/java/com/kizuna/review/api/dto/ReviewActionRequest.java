package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewCommand;

public record ReviewActionRequest(Long version, String reason, String dedupeKey) {
  public ReviewCommand.Withdraw withdrawal(String id) {
    return new ReviewCommand.Withdraw(id, version, reason, dedupeKey);
  }
}
