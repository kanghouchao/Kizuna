package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.Basis;
import java.time.OffsetDateTime;

public interface ReviewPermissionView {
  String getId();

  Basis getBasisType();

  OffsetDateTime getGrantedAt();

  String getEvidenceNote();

  Long getRecordedBy();

  String getRecorderName();

  OffsetDateTime getCreatedAt();

  OffsetDateTime getWithdrawalReceivedAt();

  String getRevocationReason();

  Long getRevokedBy();

  String getRevokerName();

  OffsetDateTime getRevokedAt();
}
