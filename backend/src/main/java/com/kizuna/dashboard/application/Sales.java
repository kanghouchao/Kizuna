package com.kizuna.dashboard.application;

import com.kizuna.reporting.dashboard.DailySales;
import com.kizuna.reporting.dashboard.SalesTotals;
import java.time.LocalDate;
import java.util.List;

public record Sales(
    LocalDate businessDate,
    SalesTotals today,
    SalesTotals monthToDate,
    SalesTotals previousMonthToDate,
    List<DailySales> daily) {
  public Sales {
    daily = List.copyOf(daily);
  }
}
