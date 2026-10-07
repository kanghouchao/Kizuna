package com.kizuna.survey.api.dto;

public record SurveyAnswerWriteResponse(
    SurveyAnswerResponse answer, SurveyOperationResponse operation)
    implements SurveyWriteResponse {}
