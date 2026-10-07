package com.kizuna.survey.domain;

import com.kizuna.survey.domain.SurveyValues.QuestionType;
import com.kizuna.survey.domain.SurveyValues.ReceivedVia;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public record SurveyAnswers(
    ReceivedVia receivedVia, OffsetDateTime receivedAt, List<Answer> answers) {
  public SurveyAnswers {
    if (receivedVia == null || answers == null || answers.isEmpty() || answers.size() > 20)
      throw SurveyInput.invalid();
    receivedAt = SurveyInput.time(receivedAt);
    var keys = new HashSet<String>();
    for (var a : answers) if (a == null || !keys.add(a.questionKey())) throw SurveyInput.invalid();
    answers = List.copyOf(answers);
  }

  public record Answer(String questionKey, String text, String optionKey) {
    public Answer {
      questionKey = SurveyInput.questionKey(questionKey);
      if ((text == null) == (optionKey == null)) throw SurveyInput.invalid();
      if (text != null) text = SurveyInput.text(text, 2000, false);
      if (optionKey != null) optionKey = SurveyInput.questionKey(optionKey);
    }
  }

  public SurveyAnswers forDefinition(List<SurveyDefinition.Question> questions) {
    var byKey = new HashMap<String, Answer>();
    for (var a : answers) byKey.put(a.questionKey(), a);
    var ordered = new ArrayList<Answer>();
    for (var q : questions) {
      var answer = byKey.remove(q.questionKey());
      if (answer == null) {
        if (q.required()) throw SurveyInput.invalid();
        continue;
      }
      if (q.type() == QuestionType.TEXT && answer.text() == null) throw SurveyInput.invalid();
      if (q.type() == QuestionType.SINGLE_CHOICE
          && (answer.optionKey() == null
              || q.options().stream().noneMatch(o -> o.optionKey().equals(answer.optionKey()))))
        throw SurveyInput.invalid();
      ordered.add(answer);
    }
    if (!byKey.isEmpty()) throw SurveyInput.invalid();
    return new SurveyAnswers(receivedVia, receivedAt, ordered);
  }
}
