package com.kizuna.recruitment.application;

import com.kizuna.cast.application.CastCreateCommand;
import com.kizuna.recruitment.domain.ApplicantIntake;
import com.kizuna.recruitment.domain.ApplicantInterview;
import com.kizuna.recruitment.domain.ApplicantStatus;

/** 応募者のユースケースが受ける命令。版は楽観ロックの照合に使い、入力の検証は要求の側で済んでいる。 */
public final class ApplicantCommands {
  private ApplicantCommands() {}

  public record IntakeUpdate(Long version, ApplicantIntake intake) {}

  public record InterviewRecord(Long version, ApplicantInterview interview) {}

  public record StatusChange(Long version, ApplicantStatus status, String reason) {}

  public record Enrollment(Long version, CastCreateCommand cast) {}
}
