package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyAnswerSummaryView;
import com.kizuna.survey.domain.SurveyValues.AnswerStatus;
import com.kizuna.survey.domain.SurveyValues.ReceivedVia;
import java.time.OffsetDateTime;

public record SurveyAnswerSummaryResponse(
    String id,
    String surveyId,
    String revisionId,
    Integer revisionNumber,
    ReceivedVia receivedVia,
    OffsetDateTime receivedAt,
    OffsetDateTime createdAt,
    AnswerStatus status,
    Long version,
    String intakeSource) {
  public static SurveyAnswerSummaryResponse of(SurveyAnswerSummaryView r) {
    return new SurveyAnswerSummaryResponse(
        r.getId(),
        r.getSurveyId(),
        r.getRevisionId(),
        r.getRevisionNumber(),
        r.getReceivedVia(),
        r.getReceivedAt(),
        r.getCreatedAt(),
        r.getStatus(),
        r.getVersion(),
        "STAFF_RECORDED");
  }
}
