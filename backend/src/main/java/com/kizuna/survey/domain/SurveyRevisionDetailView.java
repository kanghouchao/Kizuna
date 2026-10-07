package com.kizuna.survey.domain;

import java.util.List;

public interface SurveyRevisionDetailView extends SurveyRevisionSummaryView {
  String getBasedOnRevisionId();

  List<SurveyDefinition.Question> getQuestions();

  Long getRecordedBy();

  String getRecorderName();
}
