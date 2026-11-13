package com.kizuna.dashboard.api.dto;

import com.kizuna.dashboard.api.dto.SalesResponse.Totals;
import com.kizuna.dashboard.application.Sales;
import java.time.LocalDate;

public record CrossStoreSalesResponse(
    LocalDate businessDate, Totals today, Totals monthToDate, Totals previousMonthToDate) {
  public static CrossStoreSalesResponse from(Sales sales) {
    return new CrossStoreSalesResponse(
        sales.businessDate(),
        Totals.from(sales.today()),
        Totals.from(sales.monthToDate()),
        Totals.from(sales.previousMonthToDate()));
  }
}
