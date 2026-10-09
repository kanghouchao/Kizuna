package com.kizuna.advertising.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.advertising.application.AdvertisingMediaService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public record AdvertisingMediaSummaryResponse(
    long storeId,
    String month,
    long monthVersion,
    OffsetDateTime generatedAt,
    String basis,
    String mediaMatching,
    String inquiryBasis,
    long entryCount,
    long recordedTotalAmount,
    List<CategoryTotal> categoryTotals,
    Page<MediaSummary> rows) {

  public record CategoryTotal(AdvertisingCategory category, long entryCount, long recordedAmount) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MediaSummary(
      AdvertisingCategory category,
      String mediaName,
      long entryCount,
      long recordedAmount,
      long recordedInquiryEntryCount,
      long unrecordedInquiryEntryCount,
      Long recordedInquiryCountSum,
      AdvertisingMediaReport.InquiryStatus inquiryStatus) {}

  public static AdvertisingMediaSummaryResponse of(Snapshot source, Pageable page) {
    var report = source.report();
    int start = (int) Math.min(page.getOffset(), report.rows().size());
    int end = Math.min(start + page.getPageSize(), report.rows().size());
    var content =
        report.rows().subList(start, end).stream()
            .map(
                row ->
                    new MediaSummary(
                        row.category(),
                        row.mediaName(),
                        row.entryCount(),
                        row.recordedAmount(),
                        row.recordedInquiryEntryCount(),
                        row.unrecordedInquiryEntryCount(),
                        row.recordedInquiryCountSum(),
                        row.inquiryStatus()))
            .toList();
    return new AdvertisingMediaSummaryResponse(
        source.storeId(),
        source.month(),
        source.monthVersion(),
        source.generatedAt(),
        AdvertisingMediaReport.BASIS,
        AdvertisingMediaReport.MATCHING,
        AdvertisingMediaReport.INQUIRY_BASIS,
        report.entryCount(),
        report.recordedTotalAmount(),
        report.categoryTotals().stream()
            .map(c -> new CategoryTotal(c.category(), c.entryCount(), c.recordedAmount()))
            .toList(),
        new PageImpl<>(content, page, report.rows().size()));
  }
}
