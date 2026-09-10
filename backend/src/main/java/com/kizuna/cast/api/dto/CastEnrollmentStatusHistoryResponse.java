package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastEnrollmentStatus;
import java.time.OffsetDateTime;

public record CastEnrollmentStatusHistoryResponse(
    String id,
    CastEnrollmentStatus previousStatus,
    CastEnrollmentStatus newStatus,
    Long actorId,
    OffsetDateTime recordedAt) {}
