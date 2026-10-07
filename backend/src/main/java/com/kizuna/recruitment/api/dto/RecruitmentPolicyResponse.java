package com.kizuna.recruitment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RecruitmentPolicyResponse(
    boolean finalDecisionConfigured, Map<String, Integer> retentionPeriods) {
  public static RecruitmentPolicyResponse unconfigured() {
    return new RecruitmentPolicyResponse(false, null);
  }
}
