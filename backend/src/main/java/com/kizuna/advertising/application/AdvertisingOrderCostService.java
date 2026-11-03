package com.kizuna.advertising.application;

import com.kizuna.advertising.application.AdvertisingMediaComparison.Basis;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.order.reporting.AdvertisingOrderReader;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import java.time.OffsetDateTime;
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
  private final AdvertisingMediaComparison comparison;
  private final AdvertisingOrderReader orders;
  private final AppProperties properties;

  public record Snapshot(
      long storeId,
      String month,
      long monthVersion,
      OffsetDateTime generatedAt,
      AdvertisingOrderCostReport report) {}

  public Snapshot view(Long storeId, String month) {
    return AdvertisingFailures.run(
        () -> snapshot(storeId, month, new DocumentBudget(properties.getAdvertisingCost())));
  }

  public Snapshot snapshot(Long storeId, String month, DocumentBudget budget) {
    var compared =
        comparison.compare(
            storeId,
            month,
            new Basis<>(
                AdvertisingCategory.SALES, orders::read, AdvertisingOrderReader.Order::mediumId),
            budget,
            (costs, facts) ->
                AdvertisingOrderCostReport.aggregate(
                    costs.stream()
                        .map(c -> new AdvertisingOrderCostReport.Cost(c.medium(), c.amount()))
                        .toList(),
                    facts.stream()
                        .map(
                            f ->
                                new AdvertisingOrderCostReport.Order(
                                    f.medium(), f.fact().otherRoute(), f.fact().zeroAmount()))
                        .toList()));
    return new Snapshot(
        compared.storeId(),
        compared.month(),
        compared.monthVersion(),
        compared.generatedAt(),
        compared.report());
  }
}
