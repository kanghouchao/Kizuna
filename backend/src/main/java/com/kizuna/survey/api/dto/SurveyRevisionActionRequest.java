package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyInput;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SurveyRevisionActionRequest(
    @JsonDeserialize(using = SurveyVersionDeserializer.class) Long version,
    @JsonDeserialize(using = SurveyStringDeserializer.class) String reason,
    @JsonDeserialize(using = SurveyStringDeserializer.class) String dedupeKey) {
  @JsonAnySetter
  public void unknown(String key, Object value) {
    throw SurveyInput.invalid();
  }
}
