package com.kizuna.advertising.application;

import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport;
import com.kizuna.recruitment.reporting.RecruitmentHireReader;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import java.time.Clock;
import java.time.LocalDate;
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
public class AdvertisingRecruitmentEffectService {
  private final AdvertisingService costs;
  private final RecruitmentHireReader hires;
  private final AppProperties properties;
  private final Clock clock;

  public record Snapshot(
      long storeId,
      String month,
      long monthVersion,
      OffsetDateTime generatedAt,
      LocalDate retentionAsOf,
      AdvertisingRecruitmentEffectReport report) {}

  public Snapshot view(Long storeId, String month) {
    return AdvertisingFailures.run(
        () -> read(storeId, month, new DocumentBudget(properties.getAdvertisingCost())));
  }

  private Snapshot read(Long storeId, String month, DocumentBudget budget) {
    var period = YearMonth.parse(AdvertisingInput.month(month));
    var source = costs.snapshot(storeId, month);
    budget.check();
    var recruitment =
        source.costs().stream()
            .filter(c -> c.category() == AdvertisingCategory.RECRUITMENT)
            .map(
                c -> {
                  budget.text(c.mediaName());
                  return new AdvertisingRecruitmentEffectReport.Cost(c.mediaName(), c.amount());
                })
            .toList();
    var hired =
        hires
            .read(
                storeId,
                period.atDay(1),
                period.atEndOfMonth(),
                properties.getAdvertisingCost().getMaxOrders())
            .stream()
            .map(
                h -> {
                  budget.text(h.mediaName() == null ? "" : h.mediaName());
                  return new AdvertisingRecruitmentEffectReport.Hire(
                      h.mediaName(), h.startedOn(), h.endedAt());
                })
            .toList();
    var today = LocalDate.now(clock);
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(recruitment, hired, today, clock.getZone());
    budget.check();
    var summary = source.summary();
    return new Snapshot(
        AdvertisingMediaReport.safeAdd(0, summary.storeId()),
        month,
        AdvertisingMediaReport.safeAdd(0, summary.version()),
        summary.generatedAt(),
        today,
        report);
  }
}
