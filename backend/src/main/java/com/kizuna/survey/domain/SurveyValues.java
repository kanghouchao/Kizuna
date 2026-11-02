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
    CORRECTION_LINKED;

    private static final String RECEIPT_PREFIX = "SURVEY_";

    // 受領表は全モジュールで一つなので、操作名は監査の action と同じ接頭辞で区別する。
    public String receiptName() {
      return RECEIPT_PREFIX + name();
    }

    public static Operation ofReceipt(String receiptName) {
      return valueOf(receiptName.substring(RECEIPT_PREFIX.length()));
    }

    public boolean targetsAnswer() {
      return this == RECEIVED || this == WITHDRAWN || this == CORRECTION_RECEIVED;
    }
  }
}
