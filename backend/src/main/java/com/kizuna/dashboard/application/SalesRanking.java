package com.kizuna.dashboard.application;

import com.kizuna.reporting.dashboard.SalesTotals;
import com.kizuna.store.domain.StoreStatus;
import java.time.LocalDate;
import java.util.List;

public record SalesRanking(LocalDate businessDate, List<Entry> stores) {
  public SalesRanking {
    stores = List.copyOf(stores);
  }

  public record Entry(
      Long storeId, String storeName, StoreStatus storeStatus, SalesTotals totals) {}
}
