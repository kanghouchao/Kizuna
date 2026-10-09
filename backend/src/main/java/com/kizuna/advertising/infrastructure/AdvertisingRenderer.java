package com.kizuna.advertising.infrastructure;

import static com.kizuna.shared.export.TabularCells.writeCsv;
import static com.kizuna.shared.export.TabularCells.writeXlsx;

import com.kizuna.advertising.api.dto.AdvertisingResponses.CostResponse;
import com.kizuna.advertising.application.AdvertisingService.ExportSnapshot;
import com.kizuna.shared.export.DocumentBudget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class AdvertisingRenderer {
  private static final List<Object> HEADER =
      List.of(
          "record_type",
          "generated_at",
          "basis",
          "store_id",
          "month",
          "entry_count",
          "sales_amount",
          "recruitment_amount",
          "recorded_total_amount",
          "cost_id",
          "version",
          "category",
          "media_name",
          "agency_name",
          "plan_name",
          "inquiry_count",
          "amount");

  public byte[] render(ExportSnapshot report, String format, DocumentBudget budget)
      throws IOException {
    var output = budget.output();
    if (format.equals("csv")) {
      output.write(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
      writeCsv(output, HEADER, budget);
      writeCsv(output, row(report, null), budget);
      for (var cost : report.costs()) writeCsv(output, row(report, cost), budget);
    } else {
      try (var book = new SXSSFWorkbook(100)) {
        book.setCompressTempFiles(true);
        var totals = book.createSheet("条件と総計");
        var costs = book.createSheet("広告費明細");
        writeXlsx(totals, HEADER, budget);
        writeXlsx(costs, HEADER, budget);
        totals.createFreezePane(0, 1);
        costs.createFreezePane(0, 1);
        writeXlsx(totals, row(report, null), budget);
        for (var cost : report.costs()) writeXlsx(costs, row(report, cost), budget);
        budget.check();
        book.write(output);
      }
    }
    budget.check();
    return output.toByteArray();
  }

  private List<Object> row(ExportSnapshot report, CostResponse cost) {
    var s = report.summary();
    var row =
        new ArrayList<Object>(
            List.of(
                cost == null ? "metadata" : "cost",
                s.generatedAt().toString(),
                "advertising-cost-current-v1",
                s.storeId().toString(),
                s.month()));
    row.addAll(
        cost == null
            ? List.of(
                s.entryCount(), s.salesAmount(), s.recruitmentAmount(), s.recordedTotalAmount())
            : List.of("", "", "", ""));
    if (cost != null)
      row.addAll(
          List.of(
              cost.id(),
              Long.toString(cost.version()),
              cost.category().name(),
              cost.mediaName(),
              blank(cost.agencyName()),
              blank(cost.planName()),
              blank(cost.inquiryCount()),
              cost.amount()));
    while (row.size() < HEADER.size()) row.add("");
    return row;
  }

  private Object blank(Object value) {
    return value == null ? "" : value;
  }
}
