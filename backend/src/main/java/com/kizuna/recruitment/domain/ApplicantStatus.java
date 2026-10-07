package com.kizuna.recruitment.domain;

public enum ApplicantStatus {
  RECEIVED,
  SCREENING,
  INTERVIEWED,
  HIRED,
  REJECTED,
  WITHDRAWN;

  public boolean isTerminal() {
    return this == HIRED || this == REJECTED || this == WITHDRAWN;
  }
}
