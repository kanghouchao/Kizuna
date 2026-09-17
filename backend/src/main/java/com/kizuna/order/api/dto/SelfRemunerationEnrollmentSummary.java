package com.kizuna.order.api.dto;

import com.kizuna.cast.domain.CastEnrollmentStatus;
import java.time.OffsetDateTime;

public record SelfRemunerationEnrollmentSummary(
    String enrollmentId,
    Long storeId,
    String storeName,
    CastEnrollmentStatus status,
    OffsetDateTime endedAt) {}
