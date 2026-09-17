package com.kizuna.order.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SelfRemunerationChangeResponse(
    String changeId,
    String changeType,
    String orderId,
    LocalDate businessDate,
    OffsetDateTime completedAt,
    OffsetDateTime changedAt,
    String reason,
    long beforeVersion,
    long afterVersion,
    SelfRemunerationSnapshot before,
    SelfRemunerationSnapshot after) {}
