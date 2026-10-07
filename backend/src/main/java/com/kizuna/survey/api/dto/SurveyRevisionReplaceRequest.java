package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyInput;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SurveyRevisionReplaceRequest(
    @JsonDeserialize(using = SurveyVersionDeserializer.class) Long version,
    @JsonDeserialize(using = SurveyStringDeserializer.class) String title,
    List<SurveyQuestionRequest> questions,
    @JsonDeserialize(using = SurveyStringDeserializer.class) String dedupeKey) {
  @JsonAnySetter
  public void unknown(String key, Object value) {
    throw SurveyInput.invalid();
  }

  public SurveyDefinition definition() {
    if (questions == null || questions.stream().anyMatch(Objects::isNull))
      throw SurveyInput.invalid();
    return new SurveyDefinition(
        title, questions.stream().map(SurveyQuestionRequest::input).toList());
  }
}
