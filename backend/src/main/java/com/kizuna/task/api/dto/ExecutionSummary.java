package com.kizuna.task.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ExecutionSummary(
    Long id,
    Long requestId,
    int attemptNumber,
    String taskName,
    Long serviceUserId,
    String serviceName,
    Long storeId,
    String storeName,
    LocalDate periodStart,
    LocalDate periodEnd,
    String origin,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    Long processedCount,
    String failureCode) {}
