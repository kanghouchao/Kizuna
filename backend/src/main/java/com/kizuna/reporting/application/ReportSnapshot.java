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
import com.kizuna.shared.storescope.StoreScope;
import java.util.List;
import java.util.Set;
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

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public OperationalReport store(
      Long storeId,
      ReportCriteria criteria,
      boolean includeRemuneration,
      boolean includeAdvertising) {
    var facts = orders.store(storeId, criteria.from(), criteria.to());
    return OperationalReport.aggregate(
        criteria,
        facts,
        includeRemuneration
            ? remuneration.read(
                new StoreScope(false, Set.of(storeId)),
                facts,
                criteria.from(),
                criteria.to(),
                properties.getOperationalReport().getMaxOrders())
            : null,
        advertising(
            facts,
            criteria,
            includeAdvertising,
            (stores, from, to, limit) -> advertising.store(storeId, stores, from, to, limit)));
  }

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public OperationalReport groupWide(
      StoreScope scope,
      ReportCriteria criteria,
      boolean includeRemuneration,
      boolean includeAdvertising) {
    var facts = orders.groupWide(scope, criteria.from(), criteria.to());
    return OperationalReport.aggregate(
        criteria,
        facts,
        includeRemuneration
            ? remuneration.read(
                scope,
                facts,
                criteria.from(),
                criteria.to(),
                properties.getOperationalReport().getMaxOrders())
            : null,
        advertising(
            facts,
            criteria,
            includeAdvertising,
            (stores, from, to, limit) -> advertising.groupWide(scope, stores, from, to, limit)));
  }

  private AdvertisingReportFacts advertising(
      OperationalFacts facts, ReportCriteria criteria, boolean include, AdvertisingRead read) {
    if (!include) return null;
    if (AdvertisingAmounts.inapplicableStatus(criteria) != null)
      return new AdvertisingReportFacts(List.of());
    return read.apply(
        facts.stores().stream().map(OperationalFacts.Store::storeId).toList(),
        criteria.from().toString().substring(0, 7),
        criteria.to().toString().substring(0, 7),
        properties.getOperationalReport().getMaxOrders());
  }

  private interface AdvertisingRead {
    AdvertisingReportFacts apply(List<Long> stores, String from, String to, int limit);
  }
}
