package com.kizuna.survey.api.dto;

import com.kizuna.survey.application.SurveyViews.Summary;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import java.time.OffsetDateTime;

public record SurveySummaryResponse(
    String id,
    String latestRevisionId,
    int latestRevisionNumber,
    String latestTitle,
    RevisionStatus latestStatus,
    String openRevisionId,
    String draftRevisionId,
    OffsetDateTime createdAt) {
  public static SurveySummaryResponse of(Summary s) {
    return new SurveySummaryResponse(
        s.id(),
        s.latestRevisionId(),
        s.latestRevisionNumber(),
        s.latestTitle(),
        s.latestStatus(),
        s.openRevisionId(),
        s.draftRevisionId(),
        s.createdAt());
  }
}
