package com.kizuna.dashboard.api.dto;

import com.kizuna.dashboard.application.TodayOrders;
import java.time.LocalDate;

public record TodayOrdersResponse(
    LocalDate businessDate, long confirmed, long inService, long completed, long cancelled) {
  public static TodayOrdersResponse from(TodayOrders today) {
    var counts = today.counts();
    return new TodayOrdersResponse(
        today.businessDate(),
        counts.confirmed(),
        counts.inService(),
        counts.completed(),
        counts.cancelled());
  }
}
