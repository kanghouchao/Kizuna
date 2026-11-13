package com.kizuna.reporting.dashboard;

import com.kizuna.reporting.application.ReportSnapshot;
import com.kizuna.reporting.domain.ReportCriteria;
import com.kizuna.shared.storescope.StoreScope;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 店舗集合の店舗ごとの売上を、店舗別の運営集計（group_by=store）と同じ読みと数え方で返す。受注の無い店舗も 0 で並べる。 */
@Service
@RequiredArgsConstructor
public class SalesByStoreReader {
  private final ReportSnapshot snapshot;

  public List<StoreSalesTotals> read(StoreScope scope, LocalDate from, LocalDate to) {
    var report = snapshot.groupWide(scope, new ReportCriteria(from, to, "store"), false, false);
    var byStore = new HashMap<Long, SalesTotals>();
    report
        .rows()
        .forEach(
            row -> byStore.put(row.storeId(), new SalesTotals(row.totalFee(), row.orderCount())));
    return report.facts().stores().stream()
        .map(
            store ->
                new StoreSalesTotals(
                    store.storeId(),
                    store.storeName(),
                    byStore.getOrDefault(store.storeId(), new SalesTotals(0, 0))))
        .toList();
  }
}
