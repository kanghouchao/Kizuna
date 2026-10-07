package com.kizuna.survey.api.dto;

public record SurveyResponseCountsResponse(
    String surveyId,
    String revisionId,
    long totalRecords,
    long activeRecords,
    long withdrawnRecords) {}
