package com.kizuna.advertising.infrastructure;

import static com.kizuna.shared.export.TabularCells.writeCsv;
import static com.kizuna.shared.export.TabularCells.writeXlsx;

import com.kizuna.advertising.application.AdvertisingOrderCostService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.shared.export.DocumentBudget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class AdvertisingOrderCostRenderer {
  private static final List<Object> HEADER =
      List.of(
          "record_type",
          "generated_at",
          "basis",
          "media_matching",
          "order_basis",
          "rounding",
          "store_id",
          "month",
          "month_version",
          "media_name",
          "cost_entry_count",
          "recorded_sales_amount",
          "valid_completed_order_count",
          "zero_amount_order_count",
          "unnamed_media_order_count",
          "cost_per_order",
          "calculation_status",
          "interpretation");

  public byte[] render(Snapshot source, String format, DocumentBudget budget) throws IOException {
    var output = budget.output();
    var criteria = common(source, "criteria");
    var report = source.report();
    criteria.addAll(
        List.of(
            "",
            report.costEntryCount(),
            value(report.recordedSalesAmount()),
            report.validCompletedOrderCount(),
            report.zeroAmountOrderCount(),
            report.unnamedMediaOrderCount(),
            "",
            "",
            "同月同名の記録比較であり、実際の獲得費用・広告による因果・利益・ROIを示しません。0円の有効完了受注を含み、小数第3位を四捨五入しています。"));
    if (format.equals("csv")) {
      output.write(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
      writeCsv(output, HEADER, budget);
      writeCsv(output, criteria, budget);
      for (var row : report.rows()) writeCsv(output, media(source, row), budget);
    } else {
      try (var book = new SXSSFWorkbook(100)) {
        book.setCompressTempFiles(true);
        var totals = book.createSheet("条件と総計");
        var media = book.createSheet("受注あたり記録広告費");
        writeXlsx(totals, HEADER, budget);
        writeXlsx(media, HEADER, budget);
        totals.createFreezePane(0, 1);
        media.createFreezePane(0, 1);
        writeXlsx(totals, criteria, budget);
        for (var row : report.rows()) writeXlsx(media, media(source, row), budget);
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
            AdvertisingOrderCostReport.BASIS,
            AdvertisingOrderCostReport.MATCHING,
            AdvertisingOrderCostReport.ORDER_BASIS,
            AdvertisingOrderCostReport.ROUNDING,
            source.storeId(),
            source.month(),
            source.monthVersion()));
  }

  private List<Object> media(Snapshot source, AdvertisingOrderCostReport.Row value) {
    var row = common(source, "media");
    row.addAll(
        List.of(
            value.mediaName(),
            value.costEntryCount(),
            value(value.recordedSalesAmount()),
            value.validCompletedOrderCount(),
            value.zeroAmountOrderCount(),
            "",
            value(value.costPerOrder()),
            value.calculationStatus().name(),
            ""));
    return row;
  }

  private Object value(Object nullable) {
    return nullable == null ? "" : nullable;
  }
}
