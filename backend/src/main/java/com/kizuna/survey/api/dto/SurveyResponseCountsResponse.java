package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyCountsView;

public record SurveyResponseCountsResponse(
    String surveyId,
    String revisionId,
    long totalRecords,
    long activeRecords,
    long withdrawnRecords) {
  public static SurveyResponseCountsResponse of(
      String surveyId, String revisionId, SurveyCountsView count) {
    return new SurveyResponseCountsResponse(
        surveyId,
        revisionId,
        count.getTotalRecords(),
        count.getActiveRecords(),
        count.getWithdrawnRecords());
  }
}
