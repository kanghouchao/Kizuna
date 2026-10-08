package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyAnswers;

public record SurveyAnswerValueResponse(String questionKey, String text, String optionKey) {
  public static SurveyAnswerValueResponse of(SurveyAnswers.Answer a) {
    return new SurveyAnswerValueResponse(a.questionKey(), a.text(), a.optionKey());
  }
}
