package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyAnswers;
import com.kizuna.survey.domain.SurveyInput;

public record SurveyAnswerInputRequest(String questionKey, String text, String optionKey) {
  @JsonAnySetter
  public void unknown(String key, Object value) {
    throw SurveyInput.invalid();
  }

  public SurveyAnswers.Answer input() {
    return new SurveyAnswers.Answer(questionKey, text, optionKey);
  }
}
