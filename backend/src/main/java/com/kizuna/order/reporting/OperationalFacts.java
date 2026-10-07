package com.kizuna.order.reporting;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record OperationalFacts(OffsetDateTime generatedAt, List<Store> stores, List<Order> orders) {
  public OperationalFacts {
    stores = List.copyOf(stores);
    orders = List.copyOf(orders);
  }

  public record Store(Long storeId, String storeName) {}

  public record Order(
      String orderId,
      Long storeId,
      LocalDate businessDate,
      long version,
      boolean invalidated,
      int totalFee,
      int remuneration) {}
}
