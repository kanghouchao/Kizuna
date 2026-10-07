package com.kizuna.notificationdelivery.api.dto;

import com.kizuna.notificationdelivery.domain.DeliveryAttempt;
import java.time.OffsetDateTime;

public record DeliveryAttemptResponse(
    String id,
    int attemptNumber,
    String status,
    String failureCode,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    Long taskExecutionId,
    String reason) {
  public static DeliveryAttemptResponse of(DeliveryAttempt a) {
    return new DeliveryAttemptResponse(
        a.getId(),
        a.getAttemptNumber(),
        a.getStatus().name(),
        a.getFailureCode(),
        a.getStartedAt(),
        a.getFinishedAt(),
        a.getTaskExecutionId(),
        a.getReason());
  }
}
