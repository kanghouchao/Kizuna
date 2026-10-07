package com.kizuna.reporting.domain;

import com.kizuna.shared.exception.ServiceException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public record ReportCriteria(LocalDate from, LocalDate to, String groupBy) {
  public static ReportCriteria parse(String from, String to, String groupBy) {
    try {
      if (from == null
          || to == null
          || !from.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
          || !to.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
      var start = LocalDate.parse(from);
      var end = LocalDate.parse(to);
      if (start.getYear() < 1 || start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= 366)
        throw new IllegalArgumentException();
      if (!Set.of("day", "month", "store").contains(groupBy)) throw new IllegalArgumentException();
      return new ReportCriteria(start, end, groupBy);
    } catch (RuntimeException ex) {
      throw new ServiceException("営業日期間は366日以内、集計単位は day / month / store で指定してください");
    }
  }

  public String period(LocalDate day) {
    return switch (groupBy) {
      case "day" -> day.toString();
      case "month" -> day.toString().substring(0, 7);
      default -> "";
    };
  }
}
