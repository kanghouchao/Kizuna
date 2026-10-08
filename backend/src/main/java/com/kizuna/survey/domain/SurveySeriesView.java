package com.kizuna.survey.domain;

import java.time.OffsetDateTime;

public interface SurveySeriesView {
  String getId();

  String getLatestRevisionId();

  OffsetDateTime getCreatedAt();

  Long getRecordedBy();

  String getRecorderName();
}
