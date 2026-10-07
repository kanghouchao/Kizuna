package com.kizuna.task.execution;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ExecutionResult(
    Long id,
    Long requestId,
    int attemptNumber,
    Long retryOf,
    String taskName,
    String logicalKey,
    Long serviceUserId,
    String serviceName,
    Long storeId,
    String storeName,
    LocalDate periodStart,
    LocalDate periodEnd,
    String origin,
    Long initiatedBy,
    String reason,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    Long processedCount,
    String failureCode) {}
