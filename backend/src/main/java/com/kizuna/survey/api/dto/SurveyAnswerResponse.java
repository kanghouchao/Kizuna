package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyAnswer;
import com.kizuna.survey.domain.SurveyAnswerDetailView;
import com.kizuna.survey.domain.SurveyValues.AnswerStatus;
import com.kizuna.survey.domain.SurveyValues.ReceivedVia;
import java.time.OffsetDateTime;
import java.util.List;

public record SurveyAnswerResponse(
    String id,
    String surveyId,
    String revisionId,
    int revisionNumber,
    String intakeSource,
    ReceivedVia receivedVia,
    OffsetDateTime receivedAt,
    OffsetDateTime createdAt,
    AnswerStatus status,
    Long version,
    List<SurveyAnswerValueResponse> answers,
    SurveyActorResponse recordedBy,
    String supersedesId,
    String supersededById,
    String retentionPolicy) {
  public static SurveyAnswerResponse of(SurveyAnswerDetailView row) {
    return SurveyMapper.INSTANCE.answer(row);
  }

  public static SurveyAnswerResponse of(SurveyAnswer row) {
    return SurveyMapper.INSTANCE.answer(row);
  }
}
