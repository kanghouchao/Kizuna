package com.kizuna.cast.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminCastEnrollmentResponse(
    String id,
    Long storeId,
    String storeName,
    String name,
    CastEnrollmentStatus status,
    OffsetDateTime endedAt) {}
