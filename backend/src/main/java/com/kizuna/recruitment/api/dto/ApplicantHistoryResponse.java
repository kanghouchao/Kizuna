package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.domain.ApplicantStatus;
import java.time.OffsetDateTime;

public record ApplicantHistoryResponse(
    String id,
    ApplicantStatus previousStatus,
    ApplicantStatus newStatus,
    Long actorId,
    OffsetDateTime createdAt,
    String reason) {}
