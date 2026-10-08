package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyInput;
import com.kizuna.survey.domain.SurveyValues.QuestionType;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SurveyQuestionRequest(
    @JsonDeserialize(using = SurveyStringDeserializer.class) String questionKey,
    @JsonDeserialize(using = SurveyStringDeserializer.class) String type,
    @JsonDeserialize(using = SurveyStringDeserializer.class) String prompt,
    @JsonDeserialize(using = SurveyBooleanDeserializer.class) Boolean required,
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
