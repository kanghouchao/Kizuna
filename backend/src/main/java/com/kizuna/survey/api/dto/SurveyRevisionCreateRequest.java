package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyInput;
import java.util.List;
import java.util.Objects;

public record SurveyRevisionCreateRequest(
    String basedOnRevisionId,
    String title,
    List<SurveyQuestionRequest> questions,
    String dedupeKey) {
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
