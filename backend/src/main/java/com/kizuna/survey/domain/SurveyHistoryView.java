package com.kizuna.survey.domain;

import com.kizuna.survey.domain.SurveyValues.Operation;
import java.time.OffsetDateTime;

public interface SurveyHistoryView {
  String getId();

  Operation getType();

  OffsetDateTime getCreatedAt();

  Long getActorId();

  String getActorName();

  Long getBeforeVersion();

  Long getAfterVersion();

  String getBeforeStatus();

  String getAfterStatus();

  String getReason();

  String getRelatedResponseId();
}
