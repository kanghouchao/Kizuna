package com.kizuna.recruitment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.recruitment.application.ApplicantViews;
import com.kizuna.recruitment.domain.ApplicantInterview;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ReceptionChannel;
import java.time.OffsetDateTime;

public record ApplicantResponse(
    String id,
    String name,
    ApplicantStatus status,
    ReceptionChannel channel,
    ApplicantSourceType sourceType,
    @JsonInclude(JsonInclude.Include.ALWAYS) ApplicantMediumResponse medium,
    String assignee,
    OffsetDateTime createdAt,
    Long version,
    String referrer,
    String phone,
    String email,
    String address,
    String experience,
    String desiredConditions,
    ApplicantInterview interview,
    Long modifiedBy,
    OffsetDateTime updatedAt,
    String enrollmentId,
    OffsetDateTime anonymizedAt) {
  public static ApplicantResponse from(ApplicantViews.Detail a) {
    return new ApplicantResponse(
        a.id(),
        a.name(),
        a.status(),
        a.channel(),
        a.sourceType(),
        ApplicantMediumResponse.from(a.medium()),
        a.assignee(),
        a.createdAt(),
        a.version(),
        a.referrer(),
        a.phone(),
        a.email(),
        a.address(),
        a.experience(),
        a.desiredConditions(),
        a.interview(),
        a.modifiedBy(),
        a.updatedAt(),
        a.enrollmentId(),
        a.anonymizedAt());
  }
}
