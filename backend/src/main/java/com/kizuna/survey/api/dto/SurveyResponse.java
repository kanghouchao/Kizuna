package com.kizuna.survey.api.dto;

import com.kizuna.survey.application.SurveyViews.Detail;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import java.time.OffsetDateTime;

public record SurveyResponse(
    String id,
    String latestRevisionId,
    int latestRevisionNumber,
    String latestTitle,
    RevisionStatus latestStatus,
    String openRevisionId,
    String draftRevisionId,
    OffsetDateTime createdAt,
    SurveyActorResponse createdBy,
    String retentionPolicy) {
  public static SurveyResponse of(Detail d) {
    var s = d.summary();
    return new SurveyResponse(
        s.id(),
        s.latestRevisionId(),
        s.latestRevisionNumber(),
        s.latestTitle(),
        s.latestStatus(),
        s.openRevisionId(),
        s.draftRevisionId(),
        s.createdAt(),
        new SurveyActorResponse(d.recordedBy().toString(), d.recorderName()),
        "NOT_CONFIGURED");
  }
}
