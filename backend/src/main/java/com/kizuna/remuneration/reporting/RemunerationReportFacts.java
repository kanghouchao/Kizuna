package com.kizuna.remuneration.reporting;

import java.time.LocalDate;
import java.util.List;

public record RemunerationReportFacts(List<Day> days, List<Bonus> bonuses) {
  public RemunerationReportFacts {
    days = List.copyOf(days);
    bonuses = List.copyOf(bonuses);
  }

  public record Day(
      Long storeId,
      Long personId,
      LocalDate businessDate,
      long orderAmount,
      String termId,
      LocalDate effectiveFrom,
      String guaranteeState,
      Long dailyAmount,
      String closedDuration,
      boolean attendanceIncomplete,
      String guaranteeStatus,
      Long guaranteeAmount,
      long bonusAmount) {}

  public record Bonus(
      Long storeId,
      Long personId,
      String id,
      long version,
      LocalDate awardDate,
      boolean cancelled,
      long effectiveAmount) {}
}
