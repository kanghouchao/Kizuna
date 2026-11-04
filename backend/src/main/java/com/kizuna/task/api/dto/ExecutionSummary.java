package com.kizuna.task.api.dto;

import com.kizuna.task.execution.ExecutionResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ExecutionSummary(
    Long id,
    Long requestId,
    int attemptNumber,
    String taskName,
    Long serviceUserId,
    String serviceName,
    LocalDate periodStart,
    LocalDate periodEnd,
    String origin,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    Long processedCount,
    String failureCode) {
  public static ExecutionSummary of(ExecutionResult row) {
    return new ExecutionSummary(
        row.id(),
        row.requestId(),
        row.attemptNumber(),
        row.taskName(),
        row.serviceUserId(),
        row.serviceName(),
        row.periodStart(),
        row.periodEnd(),
        row.origin(),
        row.status(),
        row.startedAt(),
        row.finishedAt(),
        row.processedCount(),
        row.failureCode());
  }
}
