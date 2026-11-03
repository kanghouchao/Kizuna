package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyDefinition;
import com.kizuna.survey.domain.SurveyInput;

public record SurveyOptionRequest(String optionKey, String label) {
  @JsonAnySetter
  public void unknown(String key, Object value) {
    throw SurveyInput.invalid();
  }

  public SurveyDefinition.Option input() {
    return new SurveyDefinition.Option(optionKey, label);
  }
}
