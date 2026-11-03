package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyInput;

public record SurveyRevisionActionRequest(Long version, String reason, String dedupeKey) {
  @JsonAnySetter
  public void unknown(String key, Object value) {
    throw SurveyInput.invalid();
  }
}
