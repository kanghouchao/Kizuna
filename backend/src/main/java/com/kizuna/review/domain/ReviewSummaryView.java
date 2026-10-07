package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.review.domain.ReviewValues.Status;
import java.time.OffsetDateTime;

public interface ReviewSummaryView {
  String getId();

  String getDisplayName();

  ReceivedVia getReceivedVia();

  OffsetDateTime getReceivedAt();

  OffsetDateTime getCreatedAt();

  Status getStatus();

  PermissionStatus getPermissionStatus();

  Long getVersion();
}
