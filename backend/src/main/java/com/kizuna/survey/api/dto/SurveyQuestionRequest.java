package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyInput;
import com.kizuna.survey.domain.SurveyValues.QuestionType;
import java.util.List;
import java.util.Objects;

public record SurveyQuestionRequest(
    String questionKey,
    String type,
    String prompt,
    Boolean required,
    List<SurveyOptionRequest> options) {
  @JsonAnySetter
  public void unknown(String key, Object value) {
    throw SurveyInput.invalid();
  }

  public SurveyDefinition.Question input() {
    if (required == null || options == null || options.stream().anyMatch(Objects::isNull))
      throw SurveyInput.invalid();
    QuestionType kind;
    try {
      kind = QuestionType.valueOf(type);
    } catch (IllegalArgumentException | NullPointerException error) {
      throw SurveyInput.invalid();
    }
    return new SurveyDefinition.Question(
        questionKey,
        kind,
        prompt,
        required,
        options.stream().map(SurveyOptionRequest::input).toList());
  }
}
