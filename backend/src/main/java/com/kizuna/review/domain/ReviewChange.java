package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.Basis;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.publication.Publication;
import java.time.OffsetDateTime;

public record ReviewChange(
    String reviewId,
    Long version,
    Operation type,
    String reason,
    String key,
    Basis basis,
    OffsetDateTime eventAt,
    String evidence,
    Publication publication) {
  public ReviewChange {
    reviewId = ReviewInput.id(reviewId);
    if (version == null || version < 0 || type == null)
      throw new ServiceException("口コミの操作内容を確認してください");
    if (type == Operation.PUBLICATION_CHANGED) {
      if (publication == null) throw new ServiceException("公開設定を指定してください");
      reason = null;
    } else if (type == Operation.PERMISSION_GRANTED) {
      if (basis == null) throw new ServiceException("許可の取得根拠を指定してください");
      evidence = ReviewInput.clean(evidence, 500);
      reason = null;
    } else {
      reason = ReviewInput.clean(reason, 500);
    }
    if (type != Operation.PUBLICATION_CHANGED) publication = null;
    if (type == Operation.PERMISSION_GRANTED || type == Operation.PERMISSION_REVOKED)
      eventAt = ReviewInput.time(eventAt);
    key = ReviewInput.key(key);
  }

  public ReviewChange(
      String id,
      Long version,
      Operation type,
      String reason,
      String key,
      Basis basis,
      OffsetDateTime eventAt,
      String evidence) {
    this(id, version, type, reason, key, basis, eventAt, evidence, null);
  }

  public ReviewChange(String id, Long version, Operation type, String reason, String key) {
    this(id, version, type, reason, key, null, null, null);
  }

  public static ReviewChange publication(
      String id, Long version, Publication publication, String key) {
    return new ReviewChange(
        id, version, Operation.PUBLICATION_CHANGED, null, key, null, null, null, publication);
  }

  public String fingerprint() {
    if (publication != null)
      return ReviewFingerprint.of(
          type,
          reviewId,
          version,
          publication.published(),
          publication.publishFrom(),
          publication.publishUntil(),
          publication.displayOrder());
    return ReviewFingerprint.of(type, reviewId, version, reason, basis, eventAt, evidence);
  }
}
