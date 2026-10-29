package com.kizuna.recruitment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.settings.application.ApplicantRetentionSettings;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RecruitmentPolicyResponse(RetentionPeriods retentionPeriods) {

  /** 終端状態ごとの保持期間（月数）。未設定は null で返し、項目を省かない。 */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RetentionPeriods(
      Integer rejectedMonths, Integer withdrawnMonths, Integer hiredMonths) {}

  public static RecruitmentPolicyResponse from(ApplicantRetentionSettings settings) {
    return new RecruitmentPolicyResponse(
        new RetentionPeriods(
            settings.rejectedMonths(), settings.withdrawnMonths(), settings.hiredMonths()));
  }
}
