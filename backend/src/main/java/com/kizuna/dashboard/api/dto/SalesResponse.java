package com.kizuna.dashboard.api.dto;

import com.kizuna.dashboard.application.Sales;
import com.kizuna.reporting.dashboard.SalesTotals;
import java.time.LocalDate;
import java.util.List;

public record SalesResponse(
    LocalDate businessDate,
    Totals today,
    Totals monthToDate,
    Totals previousMonthToDate,
    List<Day> daily) {
  public record Totals(long sales, long orderCount) {
    static Totals from(SalesTotals totals) {
      return new Totals(totals.sales(), totals.orderCount());
    }
  }

  public record Day(LocalDate businessDate, long sales, long orderCount) {}

  public static SalesResponse from(Sales sales) {
    return new SalesResponse(
        sales.businessDate(),
        Totals.from(sales.today()),
        Totals.from(sales.monthToDate()),
        Totals.from(sales.previousMonthToDate()),
        sales.daily().stream()
            .map(
                day -> new Day(day.businessDate(), day.totals().sales(), day.totals().orderCount()))
            .toList());
  }
}
