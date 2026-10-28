package com.kizuna.order.application;

import com.kizuna.cast.domain.CastEnrollmentStatus;
import java.time.OffsetDateTime;

public record SelfRemunerationEnrollment(
    String enrollmentId,
    Long storeId,
    String storeName,
    CastEnrollmentStatus status,
    OffsetDateTime endedAt) {}
