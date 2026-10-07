package com.kizuna.survey.api.dto;

public sealed interface SurveyWriteResponse
    permits SurveyRevisionWriteResponse, SurveyAnswerWriteResponse {
  SurveyOperationResponse operation();
}
