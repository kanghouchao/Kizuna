package com.kizuna.advertising.application;

import com.kizuna.advertising.application.AdvertisingMediaComparison.Basis;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport;
import com.kizuna.recruitment.reporting.RecruitmentHireReader;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import java.time.Clock;
import java.time.LocalDate;
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
public class AdvertisingRecruitmentEffectService {
  private final AdvertisingMediaComparison comparison;
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
    var today = LocalDate.now(clock);
    var compared =
        comparison.compare(
            storeId,
            month,
            new Basis<>(
                AdvertisingCategory.RECRUITMENT, hires::read, RecruitmentHireReader.Hire::mediumId),
            budget,
            (costs, facts) ->
                AdvertisingRecruitmentEffectReport.aggregate(
                    costs.stream()
                        .map(
                            c ->
                                new AdvertisingRecruitmentEffectReport.Cost(c.medium(), c.amount()))
                        .toList(),
                    facts.stream()
                        .map(
                            f ->
                                new AdvertisingRecruitmentEffectReport.Hire(
                                    f.medium(), f.fact().startedOn(), f.fact().endedAt()))
                        .toList(),
                    today,
                    clock.getZone()));
    return new Snapshot(
        compared.storeId(),
        compared.month(),
        compared.monthVersion(),
        compared.generatedAt(),
        today,
        compared.report());
  }
}
