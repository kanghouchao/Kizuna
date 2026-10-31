package com.kizuna.review.domain;

import java.time.OffsetDateTime;

public interface ReviewPublicView {
  String getDisplayName();

  String getBody();

  OffsetDateTime getReceivedAt();
}
