package com.kizuna.survey.api.dto;

public record SurveyRevisionWriteResponse(
    SurveyRevisionResponse revision, SurveyOperationResponse operation)
    implements SurveyWriteResponse {}
