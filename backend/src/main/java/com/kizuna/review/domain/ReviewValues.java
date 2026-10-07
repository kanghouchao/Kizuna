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
    CORRECTION_LINKED
  }
}
