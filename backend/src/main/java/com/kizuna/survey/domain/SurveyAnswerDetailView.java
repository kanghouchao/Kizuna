package com.kizuna.survey.domain;

import java.util.List;

public interface SurveyAnswerDetailView extends SurveyAnswerSummaryView {
  List<SurveyAnswers.Answer> getAnswers();

  Long getRecordedBy();

  String getRecorderName();

  String getSupersedesId();

  String getSupersededById();
}
