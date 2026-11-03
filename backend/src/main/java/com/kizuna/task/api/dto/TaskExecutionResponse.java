package com.kizuna.task.api.dto;

import com.kizuna.task.execution.ExecutionResult;

public record TaskExecutionResponse(
    ExecutionSummary execution, String logicalKey, Long retryOf, Long initiatedBy, String reason) {
  public static TaskExecutionResponse of(ExecutionResult row) {
    return new TaskExecutionResponse(
        ExecutionSummary.of(row), row.logicalKey(), row.retryOf(), row.initiatedBy(), row.reason());
  }
}
