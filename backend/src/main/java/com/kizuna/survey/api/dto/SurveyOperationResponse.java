package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyOperation;
import com.kizuna.survey.domain.SurveyValues.Operation;

public record SurveyOperationResponse(
    String id, Operation type, String resourceId, Long committedVersion, boolean replayed) {
  public static SurveyOperationResponse of(SurveyOperation o, boolean replay) {
    return new SurveyOperationResponse(
        o.getId(),
        o.getType(),
        o.getResponseId() == null ? o.getRevisionId() : o.getResponseId(),
        o.getCommittedVersion(),
        replay);
  }
}
