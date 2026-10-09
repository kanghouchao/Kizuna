package com.kizuna.reporting.application;

import com.kizuna.order.reporting.OperationalReportReader;
import com.kizuna.remuneration.reporting.RemunerationReportReader;
import com.kizuna.reporting.domain.OperationalReport;
import com.kizuna.reporting.domain.ReportCriteria;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.storescope.StoreSetScoped;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportSnapshot {
  private final OperationalReportReader orders;
  private final RemunerationReportReader remuneration;
  private final AppProperties properties;

  @StoreScoped
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public OperationalReport store(ReportCriteria criteria, boolean include) {
    var facts = orders.store(criteria.from(), criteria.to());
    return OperationalReport.aggregate(
        criteria,
        facts,
        include
            ? remuneration.read(
                facts,
                criteria.from(),
                criteria.to(),
                properties.getOperationalReport().getMaxOrders())
            : null);
  }

  @StoreSetScoped
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public OperationalReport platform(Long store, ReportCriteria criteria, boolean include) {
    var facts = orders.platform(store, criteria.from(), criteria.to());
    return OperationalReport.aggregate(
        criteria,
        facts,
        include
            ? remuneration.read(
                facts,
                criteria.from(),
                criteria.to(),
                properties.getOperationalReport().getMaxOrders())
            : null);
  }
}
