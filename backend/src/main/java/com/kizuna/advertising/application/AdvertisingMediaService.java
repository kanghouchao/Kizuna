package com.kizuna.advertising.application;

import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdvertisingMediaService {
  private final AdvertisingService costs;
  private final AppProperties properties;

  public record Snapshot(
      long storeId,
      String month,
      long monthVersion,
      OffsetDateTime generatedAt,
      AdvertisingMediaReport report) {}

  public Snapshot snapshot(String month, DocumentBudget budget) {
    var source = costs.snapshot(month);
    budget.check();
    var entries =
        source.costs().stream()
            .map(
                cost -> {
                  budget.text(cost.mediaName());
                  return new AdvertisingMediaReport.Entry(
                      cost.category(),
                      cost.mediaName(),
                      cost.amount(),
                      cost.inquiryCount() == null ? null : cost.inquiryCount().longValue());
                })
            .toList();
    var report = AdvertisingMediaReport.aggregate(entries);
    budget.check();
    var summary = source.summary();
    return new Snapshot(
        AdvertisingMediaReport.safeAdd(0, summary.storeId()),
        summary.month(),
        AdvertisingMediaReport.safeAdd(0, summary.version()),
        summary.generatedAt(),
        report);
  }

  public Snapshot view(String month) {
    return AdvertisingFailures.run(
        () -> snapshot(month, new DocumentBudget(properties.getAdvertisingCost())));
  }
}
