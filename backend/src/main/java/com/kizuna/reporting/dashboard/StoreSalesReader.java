package com.kizuna.reporting.dashboard;

import com.kizuna.reporting.application.ReportSnapshot;
import com.kizuna.reporting.domain.ReportCriteria;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 一店舗の売上を運営集計と同じ読みと数え方（日別の運営集計）で返す。ダッシュボードが別の数え方を持たないための窓口。 */
@Service
@RequiredArgsConstructor
public class StoreSalesReader {
  private final ReportSnapshot snapshot;

  public StoreSales read(Long storeId, LocalDate from, LocalDate to) {
    var report = snapshot.store(storeId, new ReportCriteria(from, to, "day"), false, false);
    var byDay = new HashMap<String, SalesTotals>();
    report
        .rows()
        .forEach(row -> byDay.put(row.period(), new SalesTotals(row.totalFee(), row.orderCount())));
    var daily = new ArrayList<DailySales>();
    for (var day = from; !day.isAfter(to); day = day.plusDays(1))
      daily.add(new DailySales(day, byDay.getOrDefault(day.toString(), new SalesTotals(0, 0))));
    return new StoreSales(new SalesTotals(report.totalFee(), report.totalOrderCount()), daily);
  }
}
