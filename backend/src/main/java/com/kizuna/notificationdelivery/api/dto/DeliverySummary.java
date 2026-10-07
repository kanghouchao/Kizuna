package com.kizuna.notificationdelivery.api.dto;

import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import java.time.OffsetDateTime;

public record DeliverySummary(
    String id,
    String sourceType,
    String sourceId,
    String channel,
    String purpose,
    OffsetDateTime scheduledAt,
    String status,
    int attemptCount,
    OffsetDateTime createdAt,
    Long version) {
  public static DeliverySummary of(NotificationDelivery d) {
    return new DeliverySummary(
        d.getId(),
        d.getSourceType().name(),
        d.sourceId(),
        d.getChannel(),
        d.getPurpose(),
        d.getScheduledAt(),
        d.getStatus().name(),
        d.getAttemptCount(),
        d.getCreatedAt(),
        d.getVersion());
  }
}
