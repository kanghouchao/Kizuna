package com.kizuna.notificationdelivery.api.dto;

import com.kizuna.notificationdelivery.domain.DeliveryContent;
import java.time.OffsetDateTime;

public record DeliveryRequest(
    DeliveryContent.SourceType sourceType,
    String sourceId,
    String channel,
    String purpose,
    String subject,
    String body,
    OffsetDateTime scheduledAt,
    String dedupeKey) {
  public DeliveryContent content() {
    return new DeliveryContent(
        sourceType, sourceId, channel, purpose, subject, body, scheduledAt, dedupeKey);
  }
}
