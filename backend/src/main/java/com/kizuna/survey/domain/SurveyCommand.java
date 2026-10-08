package com.kizuna.survey.domain;

import com.kizuna.survey.domain.SurveyValues.Operation;

public sealed interface SurveyCommand {
  String key();

  record Create(SurveyDefinition definition, String key) implements SurveyCommand {
    public Create {
      key = SurveyInput.key(key);
    }
  }

  record Revise(String surveyId, String basedOn, SurveyDefinition definition, String key)
      implements SurveyCommand {
    public Revise {
      surveyId = SurveyInput.id(surveyId);
      basedOn = SurveyInput.id(basedOn);
      key = SurveyInput.key(key);
    }
  }

  record Replace(
      String surveyId, String revisionId, Long version, SurveyDefinition definition, String key)
      implements SurveyCommand {
    public Replace {
      surveyId = SurveyInput.id(surveyId);
      revisionId = SurveyInput.id(revisionId);
      version = SurveyInput.version(version);
      key = SurveyInput.key(key);
    }
  }

  record RevisionAction(
      String surveyId, String revisionId, Long version, Operation type, String reason, String key)
      implements SurveyCommand {
    public RevisionAction {
      surveyId = SurveyInput.id(surveyId);
      revisionId = SurveyInput.id(revisionId);
      version = SurveyInput.version(version);
      reason = SurveyInput.text(reason, 500, true);
      key = SurveyInput.key(key);
      if (type != Operation.OPENED && type != Operation.CLOSED) throw SurveyInput.invalid();
    }
  }

  record Receive(String surveyId, String revisionId, Long version, SurveyAnswers input, String key)
      implements SurveyCommand {
    public Receive {
      surveyId = SurveyInput.id(surveyId);
      revisionId = SurveyInput.id(revisionId);
      version = SurveyInput.version(version);
      key = SurveyInput.key(key);
    }
  }

  record Withdraw(String responseId, Long version, String reason, String key)
      implements SurveyCommand {
    public Withdraw {
      responseId = SurveyInput.id(responseId);
      version = SurveyInput.version(version);
      reason = SurveyInput.text(reason, 500, true);
      key = SurveyInput.key(key);
    }
  }

  record Correct(String responseId, Long version, String reason, SurveyAnswers input, String key)
      implements SurveyCommand {
    public Correct {
      responseId = SurveyInput.id(responseId);
      version = SurveyInput.version(version);
      reason = SurveyInput.text(reason, 500, true);
      key = SurveyInput.key(key);
    }
  }
}
