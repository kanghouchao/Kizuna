package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewOperation;
import com.kizuna.review.domain.ReviewValues.Operation;

public record ReviewWriteResponse(ReviewResponse review, Receipt operation) {
  public record Receipt(
      String id, Operation type, String reviewId, Long committedVersion, boolean replayed) {}

  public static ReviewWriteResponse of(
      ReviewResponse row, ReviewOperation operation, boolean replayed) {
    return new ReviewWriteResponse(
        row,
        new Receipt(
            operation.getId(),
            operation.getType(),
            operation.getReviewId(),
            operation.getCommittedVersion(),
            replayed));
  }
}
