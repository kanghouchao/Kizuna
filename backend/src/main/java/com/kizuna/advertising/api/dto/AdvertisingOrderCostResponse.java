package com.kizuna.advertising.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.advertising.application.AdvertisingOrderCostService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdvertisingOrderCostResponse(
    long storeId,
    String month,
    long monthVersion,
    OffsetDateTime generatedAt,
    String basis,
    String mediaMatching,
    String orderBasis,
    String rounding,
    long costEntryCount,
    Long recordedSalesAmount,
    long validCompletedOrderCount,
    long zeroAmountOrderCount,
    long unnamedMediaOrderCount,
    Page<OrderCostSummary> rows) {
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record OrderCostSummary(
      String mediaName,
      long costEntryCount,
      Long recordedSalesAmount,
      long validCompletedOrderCount,
      long zeroAmountOrderCount,
      String costPerOrder,
      AdvertisingOrderCostReport.Status calculationStatus) {}

  public static AdvertisingOrderCostResponse of(Snapshot source, Pageable page) {
    var report = source.report();
    int start = (int) Math.min(page.getOffset(), report.rows().size());
    int end = Math.min(start + page.getPageSize(), report.rows().size());
    var content =
        report.rows().subList(start, end).stream()
            .map(
                r ->
                    new OrderCostSummary(
                        r.mediaName(),
                        r.costEntryCount(),
                        r.recordedSalesAmount(),
                        r.validCompletedOrderCount(),
                        r.zeroAmountOrderCount(),
                        r.costPerOrder(),
                        r.calculationStatus()))
            .toList();
    return new AdvertisingOrderCostResponse(
        source.storeId(),
        source.month(),
        source.monthVersion(),
        source.generatedAt(),
        AdvertisingOrderCostReport.BASIS,
        AdvertisingOrderCostReport.MATCHING,
        AdvertisingOrderCostReport.ORDER_BASIS,
        AdvertisingOrderCostReport.ROUNDING,
        report.costEntryCount(),
        report.recordedSalesAmount(),
        report.validCompletedOrderCount(),
        report.zeroAmountOrderCount(),
        report.unnamedMediaOrderCount(),
        new PageImpl<>(content, page, report.rows().size()));
  }
}
