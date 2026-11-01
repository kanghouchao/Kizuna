package com.kizuna.reporting.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.reporting.domain.AdvertisingAmounts;
import com.kizuna.reporting.domain.OperationalReport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public record OperationalReportResponse(
    LocalDate from,
    LocalDate to,
    String groupBy,
    OffsetDateTime generatedAt,
    String basis,
    List<OperationalFacts.Store> stores,
    long totalOrderCount,
    long invalidatedOrderCount,
    long totalFee,
    long totalRemuneration,
    Page<OperationalReport.Row> rows,
    @JsonInclude(JsonInclude.Include.NON_NULL) OperationalReport.Amounts remuneration,
    @JsonInclude(JsonInclude.Include.NON_NULL) AdvertisingAmounts advertising) {
  public static OperationalReportResponse from(OperationalReport report, Pageable pageable) {
    int start = (int) Math.min(pageable.getOffset(), report.rows().size());
    int end = Math.min(start + pageable.getPageSize(), report.rows().size());
    return new OperationalReportResponse(
        report.criteria().from(),
        report.criteria().to(),
        report.criteria().groupBy(),
        report.facts().generatedAt(),
        report.basis(),
        report.facts().stores(),
        report.totalOrderCount(),
        report.invalidatedOrderCount(),
        report.totalFee(),
        report.totalRemuneration(),
        new PageImpl<>(report.rows().subList(start, end), pageable, report.rows().size()),
        report.remuneration(),
        report.advertising());
  }
}
