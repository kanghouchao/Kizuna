package com.kizuna.service.application;

import java.time.OffsetDateTime;

public record ServiceRejected(
    String enrollmentId,
    String serviceId,
    String consentEventId,
    Long actorId,
    OffsetDateTime occurredAt) {}
