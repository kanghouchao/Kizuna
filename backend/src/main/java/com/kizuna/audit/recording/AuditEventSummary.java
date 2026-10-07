package com.kizuna.audit.recording;

import java.time.OffsetDateTime;

public record AuditEventSummary(
    Long id,
    OffsetDateTime occurredAt,
    Long actorId,
    String actorType,
    String actorName,
    Long storeId,
    String action,
    String result,
    String targetType,
    String targetId,
    String sourceType,
    String sourceId) {}
