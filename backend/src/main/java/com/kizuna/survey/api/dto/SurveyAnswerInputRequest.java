package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyAnswers;
import com.kizuna.survey.domain.SurveyInput;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SurveyAnswerInputRequest(
    @JsonDeserialize(using = SurveyStringDeserializer.class) String questionKey,
    @JsonDeserialize(using = SurveyStringDeserializer.class) String text,
    @JsonDeserialize(using = SurveyStringDeserializer.class) String optionKey) {
  @JsonAnySetter
  public void unknown(String key, Object value) {
    throw SurveyInput.invalid();
  }

  public SurveyAnswers.Answer input() {
    return new SurveyAnswers.Answer(questionKey, text, optionKey);
  }
}
