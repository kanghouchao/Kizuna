package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyValues.QuestionType;
import java.util.List;

public record SurveyQuestionResponse(
    String questionKey,
    QuestionType type,
    String prompt,
    boolean required,
    List<SurveyOptionResponse> options) {
  public static SurveyQuestionResponse of(SurveyDefinition.Question q) {
    return new SurveyQuestionResponse(
        q.questionKey(),
        q.type(),
        q.prompt(),
        q.required(),
        q.options().stream().map(o -> new SurveyOptionResponse(o.optionKey(), o.label())).toList());
  }
}
