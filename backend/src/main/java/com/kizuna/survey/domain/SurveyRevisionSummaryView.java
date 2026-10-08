package com.kizuna.survey.domain;

import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import java.time.OffsetDateTime;

public interface SurveyRevisionSummaryView {
  String getId();

  String getSurveyId();

  Integer getRevisionNumber();

  String getTitle();

  RevisionStatus getStatus();

  Long getVersion();

  OffsetDateTime getCreatedAt();

  OffsetDateTime getOpenedAt();

  OffsetDateTime getClosedAt();
}
