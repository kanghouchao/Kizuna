package com.kizuna.recruitment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ReceptionChannel;
import java.time.OffsetDateTime;

public record ApplicantSummaryResponse(
    String id,
    String name,
    ApplicantStatus status,
    ReceptionChannel channel,
    ApplicantSourceType sourceType,
    @JsonInclude(JsonInclude.Include.ALWAYS) ApplicantMediumResponse medium,
    String assignee,
    OffsetDateTime createdAt,
    Long version,
    String enrollmentId,
    OffsetDateTime anonymizedAt) {}
