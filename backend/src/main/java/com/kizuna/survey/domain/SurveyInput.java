package com.kizuna.survey.domain;

import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public final class SurveyInput {
  private SurveyInput() {}

  public static ServiceException invalid() {
    return new ServiceException("アンケートの入力形式・文字数・必須項目を確認してください");
  }

  public static String text(String value, int max, boolean trim) {
    if (value == null) throw invalid();
    value = value.replace("\r\n", "\n").replace("\r", "\n");
    if (trim) value = value.strip();
    if (value.isBlank() || value.length() > max || value.indexOf('\0') >= 0) throw invalid();
    return value;
  }

  public static String id(String value) {
    if (value == null || !value.matches("[0-9]{1,32}")) throw invalid();
    return value;
  }

  public static String key(String value) {
    value = text(value, 120, true);
    if (!value.matches("[A-Za-z0-9.:_-]{1,120}")) throw invalid();
    return value;
  }

  public static String questionKey(String value) {
    value = text(value, 40, true);
    if (!value.matches("[A-Za-z0-9_-]{1,40}")) throw invalid();
    return value;
  }

  public static Long version(Long value) {
    if (value == null || value < 0 || value > Integer.MAX_VALUE) throw invalid();
    return value;
  }

  public static OffsetDateTime time(OffsetDateTime value) {
    if (value == null) throw invalid();
    return value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  public static void requirePast(OffsetDateTime value, OffsetDateTime now) {
    if (value.isBefore(OffsetDateTime.parse("2000-01-01T00:00:00Z")) || value.isAfter(now))
      throw invalid();
  }
}
