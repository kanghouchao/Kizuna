package com.kizuna.recruitment.domain;

import java.time.OffsetDateTime;

public interface ApplicantSummaryView {
  String getId();

  String getName();

  ApplicantStatus getStatus();

  ReceptionChannel getChannel();

  ApplicantSourceType getSourceType();

  String getSourceMedia();

  String getAssignee();

  OffsetDateTime getCreatedAt();

  Long getVersion();
}
