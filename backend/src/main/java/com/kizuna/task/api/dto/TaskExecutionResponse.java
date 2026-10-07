package com.kizuna.task.api.dto;

import com.kizuna.task.execution.ExecutionResult;

public record TaskExecutionResponse(
    ExecutionSummary execution, String logicalKey, Long retryOf, Long initiatedBy, String reason) {
  public static TaskExecutionResponse of(ExecutionResult row) {
    return new TaskExecutionResponse(
        new ExecutionSummary(
            row.id(),
            row.requestId(),
            row.attemptNumber(),
            row.taskName(),
            row.serviceUserId(),
            row.serviceName(),
            row.storeId(),
            row.storeName(),
            row.periodStart(),
            row.periodEnd(),
            row.origin(),
            row.status(),
            row.startedAt(),
            row.finishedAt(),
            row.processedCount(),
            row.failureCode()),
        row.logicalKey(),
        row.retryOf(),
        row.initiatedBy(),
        row.reason());
  }
}
