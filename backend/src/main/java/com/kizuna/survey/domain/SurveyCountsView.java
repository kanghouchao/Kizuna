package com.kizuna.survey.domain;

public interface SurveyCountsView {
  Long getTotalRecords();

  Long getActiveRecords();

  Long getWithdrawnRecords();
}
