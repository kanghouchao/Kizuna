package com.kizuna.advertising.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.advertising.application.AdvertisingRecruitmentEffectService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdvertisingRecruitmentEffectResponse(
    long storeId,
    String month,
    long monthVersion,
    OffsetDateTime generatedAt,
    LocalDate retentionAsOf,
    String basis,
    String mediaMatching,
    String hireBasis,
    String retentionBasis,
    String rounding,
    long costEntryCount,
    Long recordedRecruitmentAmount,
    long hireCount,
    long unnamedMediaHireCount,
    Page<RecruitmentEffectSummary> rows) {
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RecruitmentEffectSummary(
      String mediaName,
      long costEntryCount,
      Long recordedRecruitmentAmount,
      long hireCount,
      String costPerHire,
      AdvertisingRecruitmentEffectReport.Status calculationStatus,
      List<RetentionSummary> retention) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RetentionSummary(int months, long judgeableCount, long retainedCount) {}

  public static AdvertisingRecruitmentEffectResponse of(Snapshot source, Pageable page) {
    var report = source.report();
    int start = (int) Math.min(page.getOffset(), report.rows().size());
    int end = Math.min(start + page.getPageSize(), report.rows().size());
    var content =
        report.rows().subList(start, end).stream()
            .map(
                r ->
                    new RecruitmentEffectSummary(
                        r.mediaName(),
                        r.costEntryCount(),
                        r.recordedRecruitmentAmount(),
                        r.hireCount(),
                        r.costPerHire(),
                        r.calculationStatus(),
                        r.retention().stream()
                            .map(
                                t ->
                                    new RetentionSummary(
                                        t.months(), t.judgeableCount(), t.retainedCount()))
                            .toList()))
            .toList();
    return new AdvertisingRecruitmentEffectResponse(
        source.storeId(),
        source.month(),
        source.monthVersion(),
        source.generatedAt(),
        source.retentionAsOf(),
        AdvertisingRecruitmentEffectReport.BASIS,
        AdvertisingRecruitmentEffectReport.MATCHING,
        AdvertisingRecruitmentEffectReport.HIRE_BASIS,
        AdvertisingRecruitmentEffectReport.RETENTION_BASIS,
        AdvertisingRecruitmentEffectReport.ROUNDING,
        report.costEntryCount(),
        report.recordedRecruitmentAmount(),
        report.hireCount(),
        report.unnamedMediaHireCount(),
        new PageImpl<>(content, page, report.rows().size()));
  }
}
