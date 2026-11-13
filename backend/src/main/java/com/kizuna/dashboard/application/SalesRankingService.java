package com.kizuna.dashboard.application;

import com.kizuna.reporting.dashboard.SalesByStoreReader;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.store.dashboard.StoreDashboardReader;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesRankingService {
  private static final Comparator<SalesRanking.Entry> ORDER =
      Comparator.comparingLong((SalesRanking.Entry entry) -> entry.totals().sales())
          .reversed()
          .thenComparing(SalesRanking.Entry::storeId);

  private final BusinessDateService businessDates;
  private final SalesByStoreReader sales;
  private final StoreDashboardReader stores;

  // 売上と稼働状態を一つのスナップショットで読み、二つの店舗の並びが食い違わないようにする。
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public SalesRanking read(StoreScope scope) {
    var today = businessDates.currentBusinessDate();
    if (scope.storeIds().isEmpty()) return new SalesRanking(today, List.of());
    var statuses = stores.statuses(scope);
    return new SalesRanking(
        today,
        sales.read(scope, today.withDayOfMonth(1), today).stream()
            .map(
                store ->
                    new SalesRanking.Entry(
                        store.storeId(),
                        store.storeName(),
                        statuses.get(store.storeId()),
                        store.totals()))
            .sorted(ORDER)
            .toList());
  }
}
