package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public record ReviewInput(
    String body,
    String displayName,
    ReceivedVia receivedVia,
    OffsetDateTime receivedAt,
    String originOrderId) {
  public ReviewInput {
    if (body == null) throw invalid();
    body = body.replace("\r\n", "\n");
    if (body.isBlank() || body.length() > 5000 || body.indexOf('\0') >= 0 || receivedVia == null)
      throw invalid();
    displayName = displayName == null ? null : clean(displayName, 60);
    receivedAt = time(receivedAt);
    if (originOrderId != null) originOrderId = id(originOrderId);
  }

  public static String clean(String value, int max) {
    if (value == null) throw invalid();
    value = value.strip();
    if (value.isBlank() || value.length() > max || value.indexOf('\0') >= 0) throw invalid();
    return value;
  }

  public static String id(String value) {
    if (value == null || !value.matches("[0-9]{1,32}"))
      throw new ServiceException("識別子の形式を確認してください");
    return value;
  }

  public static String key(String value) {
    value = clean(value, 120);
    if (!value.matches("[a-zA-Z0-9.:_-]{1,120}")) throw invalid();
    return value;
  }

  public static OffsetDateTime time(OffsetDateTime value) {
    if (value == null) throw invalid();
    return value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  public static void requirePast(OffsetDateTime value, OffsetDateTime now) {
    if (value.isBefore(OffsetDateTime.parse("2000-01-01T00:00:00Z")) || value.isAfter(now))
      throw new ServiceException("日時は2000年以降、現在までで指定してください");
  }

  private static ServiceException invalid() {
    return new ServiceException("口コミの入力内容と文字数を確認してください");
  }
}
