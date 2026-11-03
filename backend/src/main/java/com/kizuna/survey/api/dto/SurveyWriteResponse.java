package com.kizuna.survey.api.dto;

import com.kizuna.survey.application.SurveyViews.AnswerWritten;
import com.kizuna.survey.application.SurveyViews.RevisionWritten;
import com.kizuna.survey.application.SurveyViews.Written;

public sealed interface SurveyWriteResponse
    permits SurveyRevisionWriteResponse, SurveyAnswerWriteResponse {
  SurveyOperationResponse operation();

  static SurveyWriteResponse of(Written written) {
    var operation = SurveyOperationResponse.of(written.receipt());
    return switch (written) {
      case RevisionWritten w ->
          new SurveyRevisionWriteResponse(SurveyRevisionResponse.of(w.revision()), operation);
      case AnswerWritten w ->
          new SurveyAnswerWriteResponse(SurveyAnswerResponse.of(w.answer()), operation);
    };
  }
}
