package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyHistoryView;
import com.kizuna.survey.domain.SurveyValues.Operation;
import java.time.OffsetDateTime;

public record SurveyRevisionHistoryResponse(
    String id,
    Operation type,
    OffsetDateTime createdAt,
    SurveyActorResponse actor,
    Long beforeVersion,
    Long afterVersion,
    String beforeStatus,
    String afterStatus,
    String reason) {
  public static SurveyRevisionHistoryResponse of(SurveyHistoryView r) {
    return new SurveyRevisionHistoryResponse(
        r.getId(),
        r.getType(),
        r.getCreatedAt(),
        new SurveyActorResponse(r.getActorId().toString(), r.getActorName()),
        r.getBeforeVersion(),
        r.getAfterVersion(),
        r.getBeforeStatus(),
        r.getAfterStatus(),
        r.getReason());
  }
}
