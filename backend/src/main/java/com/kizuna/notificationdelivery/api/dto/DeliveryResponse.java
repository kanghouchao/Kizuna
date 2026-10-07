package com.kizuna.notificationdelivery.api.dto;

import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import java.time.OffsetDateTime;

public record DeliveryResponse(
    String id,
    String sourceType,
    String sourceId,
    String channel,
    String purpose,
    String subject,
    String body,
    OffsetDateTime scheduledAt,
    String dedupeKey,
    String status,
    int attemptCount,
    OffsetDateTime createdAt,
    Long version,
    String contactDecision,
    String transportAvailability,
    String schedulingAvailability) {
  public static DeliveryResponse of(NotificationDelivery d, String decision, boolean available) {
    return new DeliveryResponse(
        d.getId(),
        d.getSourceType().name(),
        d.sourceId(),
        d.getChannel(),
        d.getPurpose(),
        d.getSubject(),
        d.getBody(),
        d.getScheduledAt(),
        d.getDedupeKey(),
        d.getStatus().name(),
        d.getAttemptCount(),
        d.getCreatedAt(),
        d.getVersion(),
        decision,
        available ? "AVAILABLE" : "UNAVAILABLE",
        "MANUAL_TASK_ONLY");
  }
}
