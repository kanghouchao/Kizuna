package com.kizuna.advertising.infrastructure;

import static com.kizuna.shared.export.TabularCells.writeCsv;
import static com.kizuna.shared.export.TabularCells.writeXlsx;

import com.kizuna.advertising.application.AdvertisingMediaService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.shared.export.DocumentBudget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class AdvertisingMediaRenderer {
  private static final List<Object> HEADER =
      List.of(
          "record_type",
          "generated_at",
          "basis",
          "media_matching",
          "inquiry_basis",
          "store_id",
          "month",
          "month_version",
          "category",
          "media_name",
          "entry_count",
          "recorded_amount",
          "recorded_inquiry_entry_count",
          "unrecorded_inquiry_entry_count",
          "recorded_inquiry_count_sum",
          "inquiry_status");

  public byte[] render(Snapshot source, String format, DocumentBudget budget) throws IOException {
    var output = budget.output();
    var metadata = new ArrayList<List<Object>>();
    var criteria = common(source, "criteria");
    criteria.addAll(
        List.of(
            "",
            "",
            source.report().entryCount(),
            source.report().recordedTotalAmount(),
            "",
            "",
            "",
            ""));
    metadata.add(criteria);
    for (var category : source.report().categoryTotals()) {
      var row = common(source, "category_total");
      row.addAll(
          List.of(
              category.category().name(),
              "",
              category.entryCount(),
              category.recordedAmount(),
              "",
              "",
              "",
              ""));
      metadata.add(row);
    }
    if (format.equals("csv")) {
      output.write(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
      writeCsv(output, HEADER, budget);
      for (var row : metadata) writeCsv(output, row, budget);
      for (var row : source.report().rows()) writeCsv(output, media(source, row), budget);
    } else {
      try (var book = new SXSSFWorkbook(100)) {
        book.setCompressTempFiles(true);
        var totals = book.createSheet("条件と総計");
        var media = book.createSheet("媒体別集計");
        writeXlsx(totals, HEADER, budget);
        writeXlsx(media, HEADER, budget);
        totals.createFreezePane(0, 1);
        media.createFreezePane(0, 1);
        for (var row : metadata) writeXlsx(totals, row, budget);
        for (var row : source.report().rows()) writeXlsx(media, media(source, row), budget);
        budget.check();
        book.write(output);
      }
    }
    budget.check();
    return output.toByteArray();
  }

  private ArrayList<Object> common(Snapshot source, String type) {
    return new ArrayList<>(
        List.of(
            type,
            source.generatedAt().toString(),
            AdvertisingMediaReport.BASIS,
            AdvertisingMediaReport.MATCHING,
            AdvertisingMediaReport.INQUIRY_BASIS,
            source.storeId(),
            source.month(),
            source.monthVersion()));
  }

  private List<Object> media(Snapshot source, AdvertisingMediaReport.Row value) {
    var row = common(source, "media");
    row.addAll(
        List.of(
            value.category().name(),
            value.mediaName(),
            value.entryCount(),
            value.recordedAmount(),
            value.recordedInquiryEntryCount(),
            value.unrecordedInquiryEntryCount(),
            value.recordedInquiryCountSum() == null ? "" : value.recordedInquiryCountSum(),
            value.inquiryStatus().name()));
    return row;
  }
}
