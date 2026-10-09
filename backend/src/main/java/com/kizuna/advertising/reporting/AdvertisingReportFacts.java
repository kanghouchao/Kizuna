package com.kizuna.advertising.reporting;

import java.util.List;

public record AdvertisingReportFacts(List<Cost> costs) {
  public AdvertisingReportFacts {
    costs = List.copyOf(costs);
  }

  public record Cost(
      Long storeId, String id, long version, String month, String category, long amount) {}
}
