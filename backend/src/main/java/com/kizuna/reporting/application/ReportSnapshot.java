package com.kizuna.reporting.application;

import com.kizuna.advertising.reporting.AdvertisingReportFacts;
import com.kizuna.advertising.reporting.AdvertisingReportReader;
import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.order.reporting.OperationalReportReader;
import com.kizuna.remuneration.reporting.RemunerationReportReader;
import com.kizuna.reporting.domain.AdvertisingAmounts;
import com.kizuna.reporting.domain.OperationalReport;
import com.kizuna.reporting.domain.ReportCriteria;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.storescope.StoreSetScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportSnapshot {
  private final OperationalReportReader orders;
  private final RemunerationReportReader remuneration;
  private final AdvertisingReportReader advertising;
  private final AppProperties properties;

  @StoreScoped
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public OperationalReport store(
      ReportCriteria criteria, boolean includeRemuneration, boolean includeAdvertising) {
    var facts = orders.store(criteria.from(), criteria.to());
    return OperationalReport.aggregate(
        criteria,
        facts,
        includeRemuneration
            ? remuneration.read(
                facts,
                criteria.from(),
                criteria.to(),
                properties.getOperationalReport().getMaxOrders())
            : null,
        advertising(false, facts, criteria, includeAdvertising));
  }

  @StoreSetScoped
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public OperationalReport platform(
      Long store,
      ReportCriteria criteria,
      boolean includeRemuneration,
      boolean includeAdvertising) {
    var facts = orders.platform(store, criteria.from(), criteria.to());
    return OperationalReport.aggregate(
        criteria,
        facts,
        includeRemuneration
            ? remuneration.read(
                facts,
                criteria.from(),
                criteria.to(),
                properties.getOperationalReport().getMaxOrders())
            : null,
        advertising(true, facts, criteria, includeAdvertising));
  }

  private AdvertisingReportFacts advertising(
      boolean platform, OperationalFacts facts, ReportCriteria criteria, boolean include) {
    if (!include) return null;
    if (AdvertisingAmounts.inapplicableStatus(criteria) != null)
      return new AdvertisingReportFacts(List.of());
    return advertising.read(
        platform,
        facts.stores().stream().map(OperationalFacts.Store::storeId).toList(),
        criteria.from().toString().substring(0, 7),
        criteria.to().toString().substring(0, 7),
        properties.getOperationalReport().getMaxOrders());
  }
}
