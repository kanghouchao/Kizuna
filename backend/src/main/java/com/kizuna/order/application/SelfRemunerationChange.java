package com.kizuna.order.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SelfRemunerationChange(
    String changeId,
    String changeType,
    String orderId,
    LocalDate businessDate,
    OffsetDateTime completedAt,
    OffsetDateTime changedAt,
    String reason,
    long beforeVersion,
    long afterVersion,
    SelfRemunerationState before,
    SelfRemunerationState after) {}
