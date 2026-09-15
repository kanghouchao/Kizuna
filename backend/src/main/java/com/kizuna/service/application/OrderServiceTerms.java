package com.kizuna.service.application;

import java.time.OffsetDateTime;

public record OrderServiceTerms(
    String serviceId,
    String revisionId,
    long revisionNumber,
    String name,
    Integer durationMinutes,
    int price,
    int remuneration,
    OffsetDateTime occurredAt,
    boolean serviceDeleted) {}
