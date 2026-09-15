package com.kizuna.service.application;

import java.time.OffsetDateTime;

public record CourseTerms(
    String serviceId,
    String revisionId,
    long revisionNumber,
    String name,
    int durationMinutes,
    int price,
    int remuneration,
    OffsetDateTime occurredAt,
    boolean serviceDeleted) {}
