package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyInput;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SurveyOptionRequest(
    @JsonDeserialize(using = SurveyStringDeserializer.class) String optionKey,
    @JsonDeserialize(using = SurveyStringDeserializer.class) String label) {
  @JsonAnySetter
  public void unknown(String key, Object value) {
    throw SurveyInput.invalid();
  }

  public SurveyDefinition.Option input() {
    return new SurveyDefinition.Option(optionKey, label);
  }
}
