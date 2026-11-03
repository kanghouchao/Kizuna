package com.kizuna.survey.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kizuna.survey.domain.SurveyAnswers;
import com.kizuna.survey.domain.SurveyInput;
import com.kizuna.survey.domain.SurveyValues.ReceivedVia;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record SurveyAnswerCorrectionRequest(
    Long version,
    String reason,
    String receivedVia,
    OffsetDateTime receivedAt,
    List<SurveyAnswerInputRequest> answers,
    String dedupeKey) {
  @JsonAnySetter
  public void unknown(String key, Object value) {
    throw SurveyInput.invalid();
  }

  public SurveyAnswers input() {
    if (answers == null || answers.stream().anyMatch(Objects::isNull)) throw SurveyInput.invalid();
    ReceivedVia via;
    try {
      via = ReceivedVia.valueOf(receivedVia);
    } catch (IllegalArgumentException | NullPointerException error) {
      throw SurveyInput.invalid();
    }
    return new SurveyAnswers(
        via, receivedAt, answers.stream().map(SurveyAnswerInputRequest::input).toList());
  }
}
