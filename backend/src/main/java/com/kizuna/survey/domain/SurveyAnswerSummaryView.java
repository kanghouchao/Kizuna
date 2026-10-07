package com.kizuna.survey.domain;

import com.kizuna.survey.domain.SurveyValues.AnswerStatus;
import com.kizuna.survey.domain.SurveyValues.ReceivedVia;
import java.time.OffsetDateTime;

public interface SurveyAnswerSummaryView {
  String getId();

  String getSurveyId();

  String getRevisionId();

  Integer getRevisionNumber();

  ReceivedVia getReceivedVia();

  OffsetDateTime getReceivedAt();

  OffsetDateTime getCreatedAt();

  AnswerStatus getStatus();

  Long getVersion();
}
