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
  private final AdvertisingMediaComparison comparison;
  private final AppProperties properties;

  public record Snapshot(
      long storeId,
      String month,
      long monthVersion,
      OffsetDateTime generatedAt,
      AdvertisingMediaReport report) {}

  public Snapshot snapshot(Long storeId, String month, DocumentBudget budget) {
    var compared =
        comparison.costs(
            storeId,
            month,
            budget,
            costs ->
                AdvertisingMediaReport.aggregate(
                    costs.stream()
                        .map(
                            c ->
                                new AdvertisingMediaReport.Entry(
                                    c.category(),
                                    c.medium(),
                                    c.amount(),
                                    c.inquiryCount() == null ? null : c.inquiryCount().longValue()))
                        .toList()));
    return new Snapshot(
        compared.storeId(),
        compared.month(),
        compared.monthVersion(),
        compared.generatedAt(),
        compared.report());
  }

  public Snapshot view(Long storeId, String month) {
    return AdvertisingFailures.run(
        () -> snapshot(storeId, month, new DocumentBudget(properties.getAdvertisingCost())));
  }
}
