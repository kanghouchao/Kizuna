package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyRevisionSummaryView;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import java.time.OffsetDateTime;

public record SurveyRevisionSummaryResponse(
    String id,
    String surveyId,
    Integer revisionNumber,
    String title,
    RevisionStatus status,
    Long version,
    OffsetDateTime createdAt,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt) {
  public static SurveyRevisionSummaryResponse of(SurveyRevisionSummaryView r) {
    return new SurveyRevisionSummaryResponse(
        r.getId(),
        r.getSurveyId(),
        r.getRevisionNumber(),
        r.getTitle(),
        r.getStatus(),
        r.getVersion(),
        r.getCreatedAt(),
        r.getOpenedAt(),
        r.getClosedAt());
  }
}
