package com.kizuna.review.api.dto;

import com.kizuna.receipt.once.Receipt;
import com.kizuna.review.domain.ReviewValues.Operation;

public record ReviewWriteResponse(ReviewResponse review, OperationResult operation) {
  public record OperationResult(
      String id, Operation type, String reviewId, Long committedVersion, boolean replayed) {}

  /** 受領の種別・対象・成立時の版は受領の行から、口コミの状態は現在のものから組む。 */
  public static ReviewWriteResponse of(ReviewResponse current, Receipt receipt) {
    return new ReviewWriteResponse(
        current,
        new OperationResult(
            receipt.id(),
            Operation.ofReceipt(receipt.operation()),
            receipt.targetId(),
            receipt.longFact("committed_version"),
            receipt.replayed()));
  }
}
