package com.kizuna.review.domain;

import com.kizuna.shared.exception.ServiceException;

public record ReviewCorrection(
    String reviewId, Long version, String reason, ReviewInput input, String key) {
  public ReviewCorrection {
    reviewId = ReviewInput.id(reviewId);
    reason = ReviewInput.clean(reason, 500);
    key = ReviewInput.key(key);
    if (version == null || version < 0 || input == null)
      throw new ServiceException("訂正内容を確認してください");
  }

  public String fingerprint() {
    return ReviewFingerprint.of(
        "CORRECTION_RECEIVED", reviewId, version, reason, ReviewFingerprint.receipt(input));
  }
}
