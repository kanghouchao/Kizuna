package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.publication.Publication;
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

  Publication getPublication();

  default boolean displayedAt(OffsetDateTime now) {
    return getStatus() == Status.APPROVED
        && getPermissionStatus() == PermissionStatus.GRANTED
        && getPublication().shownAt(now);
  }
}
