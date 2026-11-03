package com.kizuna.review.api.dto;

import com.kizuna.review.application.ReviewViews;
import com.kizuna.review.domain.ReviewValues.Operation;

public record ReviewWriteResponse(ReviewResponse review, OperationResult operation) {
  public record OperationResult(
      String id, Operation type, String reviewId, Long committedVersion, boolean replayed) {}

  /** 受領の種別・対象・成立時の版は受領の行から、口コミの状態は現在のものから組む。 */
  public static ReviewWriteResponse of(ReviewViews.Written written) {
    var receipt = written.receipt();
    return new ReviewWriteResponse(
        ReviewResponse.of(written.review()),
        new OperationResult(
            receipt.id(),
            Operation.ofReceipt(receipt.operation()),
            receipt.targetId(),
            receipt.longFact("committed_version"),
            receipt.replayed()));
  }
}
