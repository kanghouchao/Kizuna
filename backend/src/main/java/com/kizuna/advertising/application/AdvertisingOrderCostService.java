package com.kizuna.advertising.application;

import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.order.reporting.AdvertisingOrderReader;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import com.kizuna.shared.storescope.StoreScoped;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(
    readOnly = true,
    isolation = Isolation.REPEATABLE_READ,
    timeoutString = "#{@appProperties.advertisingCost.readTimeoutSeconds}")
public class AdvertisingOrderCostService {
  private final AdvertisingService costs;
  private final AdvertisingOrderReader orders;
  private final AppProperties properties;

  public record Snapshot(
      long storeId,
      String month,
      long monthVersion,
      OffsetDateTime generatedAt,
      AdvertisingOrderCostReport report) {}

  @StoreScoped
  public Snapshot view(String month) {
    return AdvertisingFailures.run(
        () -> read(month, new DocumentBudget(properties.getAdvertisingCost())));
  }

  @StoreScoped
  public Snapshot snapshot(String month, DocumentBudget budget) {
    return read(month, budget);
  }

  private Snapshot read(String month, DocumentBudget budget) {
    var period = YearMonth.parse(AdvertisingInput.month(month));
    var source = costs.snapshot(month);
    budget.check();
    var sales =
        source.costs().stream()
            .filter(c -> c.category() == AdvertisingCategory.SALES)
            .map(
                c -> {
                  budget.text(c.mediaName());
                  return new AdvertisingOrderCostReport.Cost(c.mediaName(), c.amount());
                })
            .toList();
    var validOrders =
        orders
            .read(
                period.atDay(1),
                period.atEndOfMonth(),
                properties.getAdvertisingCost().getMaxOrders())
            .stream()
            .map(
                o -> {
                  budget.text(o.mediaName() == null ? "" : o.mediaName());
                  return new AdvertisingOrderCostReport.Order(o.mediaName(), o.zeroAmount());
                })
            .toList();
    var report = AdvertisingOrderCostReport.aggregate(sales, validOrders);
    budget.check();
    var summary = source.summary();
    return new Snapshot(
        AdvertisingMediaReport.safeAdd(0, summary.storeId()),
        month,
        AdvertisingMediaReport.safeAdd(0, summary.version()),
        summary.generatedAt(),
        report);
  }
}
