package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastEnrollmentStatus;
import java.time.OffsetDateTime;

public record CastEnrollmentStatusResponse(
    String id, CastEnrollmentStatus status, OffsetDateTime endedAt) {}
