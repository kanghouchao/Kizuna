package com.kizuna.dashboard.api.dto;

import com.kizuna.dashboard.application.SalesRanking;
import com.kizuna.store.domain.StoreStatus;
import java.time.LocalDate;
import java.util.List;

public record SalesRankingResponse(LocalDate businessDate, List<StoreSummary> stores) {
  public record StoreSummary(
      Long storeId, String storeName, StoreStatus storeStatus, long sales, long orderCount) {}

  public static SalesRankingResponse from(SalesRanking ranking) {
    return new SalesRankingResponse(
        ranking.businessDate(),
        ranking.stores().stream()
            .map(
                store ->
                    new StoreSummary(
                        store.storeId(),
                        store.storeName(),
                        store.storeStatus(),
                        store.totals().sales(),
                        store.totals().orderCount()))
            .toList());
  }
}
