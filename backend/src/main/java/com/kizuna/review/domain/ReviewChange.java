package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.Basis;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;

public record ReviewChange(
    String reviewId,
    Long version,
    Operation type,
    String reason,
    String key,
    Basis basis,
    OffsetDateTime eventAt,
    String evidence) {
  public ReviewChange {
    reviewId = ReviewInput.id(reviewId);
    if (version == null || version < 0 || type == null)
      throw new ServiceException("口コミの操作内容を確認してください");
    if (type == Operation.PERMISSION_GRANTED) {
      if (basis == null) throw new ServiceException("許可の取得根拠を指定してください");
      evidence = ReviewInput.clean(evidence, 500);
      reason = null;
    } else {
      reason = ReviewInput.clean(reason, 500);
    }
    if (type == Operation.PERMISSION_GRANTED || type == Operation.PERMISSION_REVOKED)
      eventAt = ReviewInput.time(eventAt);
    key = ReviewInput.key(key);
  }

  public ReviewChange(String id, Long version, Operation type, String reason, String key) {
    this(id, version, type, reason, key, null, null, null);
  }

  public String fingerprint() {
    return ReviewFingerprint.of(type, reviewId, version, reason, basis, eventAt, evidence);
  }
}
