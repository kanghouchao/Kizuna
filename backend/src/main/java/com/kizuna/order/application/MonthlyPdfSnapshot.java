package com.kizuna.order.application;

import com.kizuna.order.api.dto.MonthlyRemunerationOrderSummary;
import java.time.OffsetDateTime;
import java.util.List;

public record MonthlyPdfSnapshot(
    String storeName,
    String castName,
    String month,
    OffsetDateTime generatedAt,
    long total,
    List<MonthlyRemunerationOrderSummary> orders) {
  public MonthlyPdfSnapshot {
    orders = List.copyOf(orders);
  }
}
