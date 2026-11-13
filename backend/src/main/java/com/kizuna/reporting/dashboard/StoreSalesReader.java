package com.kizuna.reporting.dashboard;

import com.kizuna.reporting.application.ReportSnapshot;
import com.kizuna.reporting.domain.OperationalReport;
import com.kizuna.reporting.domain.ReportCriteria;
import com.kizuna.shared.storescope.StoreScope;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 売上を運営集計と同じ読みと数え方（日別の運営集計）で返す。ダッシュボードが別の数え方を持たないための窓口。 */
@Service
@RequiredArgsConstructor
public class StoreSalesReader {
  private final ReportSnapshot snapshot;

  public StoreSales read(Long storeId, LocalDate from, LocalDate to) {
    var criteria = new ReportCriteria(from, to, "day");
    return sales(snapshot.store(storeId, criteria, false, false), from, to);
  }

  /** 店舗集合の合算。空集合は全店と読み替えず、運営集計の読み取りが問い合わせ前に 0 で返す。 */
  public StoreSales groupWide(StoreScope scope, LocalDate from, LocalDate to) {
    var criteria = new ReportCriteria(from, to, "day");
    return sales(snapshot.groupWide(scope, criteria, false, false), from, to);
  }

  private static StoreSales sales(OperationalReport report, LocalDate from, LocalDate to) {
    var byDay = new HashMap<String, SalesTotals>();
    // 行は店舗 × 日なので、店舗集合では同じ日の行を足し合わせる。
    report
        .rows()
        .forEach(
            row ->
                byDay.merge(
                    row.period(),
                    new SalesTotals(row.totalFee(), row.orderCount()),
                    (a, b) ->
                        new SalesTotals(a.sales() + b.sales(), a.orderCount() + b.orderCount())));
    var daily = new ArrayList<DailySales>();
    for (var day = from; !day.isAfter(to); day = day.plusDays(1))
      daily.add(new DailySales(day, byDay.getOrDefault(day.toString(), new SalesTotals(0, 0))));
    return new StoreSales(new SalesTotals(report.totalFee(), report.totalOrderCount()), daily);
  }
}
