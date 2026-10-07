package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyRevision;
import com.kizuna.survey.domain.SurveyRevisionDetailView;
import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record SurveyRevisionResponse(
    String id,
    String surveyId,
    int revisionNumber,
    String title,
    RevisionStatus status,
    Long version,
    OffsetDateTime createdAt,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    String basedOnRevisionId,
    List<SurveyQuestionResponse> questions,
    SurveyActorResponse createdBy,
    String retentionPolicy) {
  public static SurveyRevisionResponse of(SurveyRevisionDetailView row) {
    return SurveyMapper.INSTANCE.revision(row);
  }

  public static SurveyRevisionResponse of(SurveyRevision row) {
    return SurveyMapper.INSTANCE.revision(row);
  }
}
