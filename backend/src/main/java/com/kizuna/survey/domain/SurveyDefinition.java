package com.kizuna.survey.domain;

import com.kizuna.survey.domain.SurveyValues.QuestionType;
import java.util.HashSet;
import java.util.List;

public record SurveyDefinition(String title, List<Question> questions) {
  public SurveyDefinition {
    title = SurveyInput.text(title, 120, true);
    if (questions == null || questions.isEmpty() || questions.size() > 20)
      throw SurveyInput.invalid();
    var keys = new HashSet<String>();
    for (var q : questions)
      if (q == null || !keys.add(q.questionKey())) throw SurveyInput.invalid();
    questions = List.copyOf(questions);
  }

  public record Question(
      String questionKey,
      QuestionType type,
      String prompt,
      boolean required,
      List<Option> options) {
    public Question {
      questionKey = SurveyInput.questionKey(questionKey);
      prompt = SurveyInput.text(prompt, 500, true);
      if (type == null
          || options == null
          || (type == QuestionType.TEXT && !options.isEmpty())
          || (type == QuestionType.SINGLE_CHOICE && (options.size() < 2 || options.size() > 10)))
        throw SurveyInput.invalid();
      var keys = new HashSet<String>();
      var labels = new HashSet<String>();
      for (var o : options)
        if (o == null || !keys.add(o.optionKey()) || !labels.add(o.label()))
          throw SurveyInput.invalid();
      options = List.copyOf(options);
    }
  }

  public record Option(String optionKey, String label) {
    public Option {
      optionKey = SurveyInput.questionKey(optionKey);
      label = SurveyInput.text(label, 120, true);
    }
  }
}
