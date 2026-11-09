package com.kizuna.notificationdelivery.domain;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.GeneratedIds;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public record DeliveryContent(
    DeliverySourceType sourceType,
    String sourceId,
    DeliveryChannel channel,
    DeliveryPurpose purpose,
    String subject,
    String body,
    OffsetDateTime scheduledAt,
    String dedupeKey) {
  public DeliveryContent {
    sourceId = clean(sourceId, 36);
    subject = clean(subject, 200);
    dedupeKey = clean(dedupeKey, 120);
    if (sourceType == null
        || !(sourceType == DeliverySourceType.MEMBER
            ? sourceId.matches("[0-9]{1,32}")
            : GeneratedIds.matches(sourceId))
        || channel == null
        || purpose == null
        || !dedupeKey.matches("[a-zA-Z0-9.:_-]{1,120}")
        || body == null
        || body.isBlank()
        || body.length() > 10000
        || body.indexOf('\0') >= 0
        || subject.indexOf('\n') >= 0
        || subject.indexOf('\r') >= 0
        || scheduledAt == null) {
      throw new ServiceException("起点・通知の内容・予約日時・要求キーを確認してください");
    }
    body = body.replace("\r\n", "\n");
    scheduledAt = scheduledAt.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  public void validateNewSchedule(OffsetDateTime now) {
    if (scheduledAt.isBefore(OffsetDateTime.parse("2000-01-01T00:00:00Z"))
        || scheduledAt.isAfter(now.plusDays(366)))
      throw new ServiceException("予約日時は2000年以降、現在から366日以内で指定してください");
  }

  public static String clean(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0)
      throw new ServiceException("入力の文字数と内容を確認してください");
    return value.strip();
  }
}
