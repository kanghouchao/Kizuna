package com.kizuna.order.application;

import com.kizuna.order.domain.RemunerationOrderRow;
import java.time.OffsetDateTime;
import java.util.List;

public record MonthlyPdfSnapshot(
    String storeName,
    String castName,
    String month,
    OffsetDateTime generatedAt,
    long total,
    List<RemunerationOrderRow> orders) {
  public MonthlyPdfSnapshot {
    orders = List.copyOf(orders);
  }
}
