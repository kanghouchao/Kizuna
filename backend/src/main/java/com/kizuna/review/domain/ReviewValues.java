package com.kizuna.review.domain;

public final class ReviewValues {
  private ReviewValues() {}

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN
  }

  public enum PermissionStatus {
    NOT_GRANTED,
    GRANTED,
    REVOKED
  }

  public enum ReceivedVia {
    PAPER,
    VERBAL,
    ELECTRONIC
  }

  public enum Basis {
    WRITTEN,
    VERBAL,
    ELECTRONIC_RECORD
  }

  public enum Operation {
    RECEIVED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    CORRECTION_RECEIVED,
    CORRECTION_LINKED,
    PUBLICATION_CHANGED;

    private static final String RECEIPT_PREFIX = "REVIEW_";

    // 受領表は全モジュールで一つなので、操作名は監査の action と同じ接頭辞で区別する。
    public String receiptName() {
      return RECEIPT_PREFIX + name();
    }

    public static Operation ofReceipt(String receiptName) {
      return valueOf(receiptName.substring(RECEIPT_PREFIX.length()));
    }
  }
}
