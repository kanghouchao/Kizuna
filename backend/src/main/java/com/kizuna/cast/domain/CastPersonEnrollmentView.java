package com.kizuna.cast.domain;

import java.time.OffsetDateTime;

public interface CastPersonEnrollmentView {
  String getId();

  Long getStoreId();

  String getStoreName();

  String getName();

  CastEnrollmentStatus getStatus();

  OffsetDateTime getEndedAt();
}
