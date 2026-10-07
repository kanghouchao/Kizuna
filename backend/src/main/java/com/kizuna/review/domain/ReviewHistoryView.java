package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import java.time.OffsetDateTime;

public interface ReviewHistoryView {
  String getId();

  Operation getType();

  OffsetDateTime getCreatedAt();

  Long getActorId();

  String getActorName();

  Long getBeforeVersion();

  Long getAfterVersion();

  Status getBeforeStatus();

  Status getAfterStatus();

  PermissionStatus getBeforePermissionStatus();

  PermissionStatus getAfterPermissionStatus();

  String getReason();

  String getRelatedReviewId();

  String getPermissionRecordId();
}
