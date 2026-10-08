package com.kizuna.survey.domain;

public final class SurveyValues {
  private SurveyValues() {}

  public enum RevisionStatus {
    DRAFT,
    OPEN,
    CLOSED
  }

  public enum AnswerStatus {
    ACTIVE,
    WITHDRAWN
  }

  public enum QuestionType {
    TEXT,
    SINGLE_CHOICE
  }

  public enum ReceivedVia {
    PAPER,
    VERBAL,
    EXISTING_RECORD
  }

  public enum Operation {
    DRAFT_CREATED,
    DRAFT_REPLACED,
    OPENED,
    CLOSED,
    RECEIVED,
    WITHDRAWN,
    CORRECTION_RECEIVED,
    CORRECTION_LINKED
  }
}
