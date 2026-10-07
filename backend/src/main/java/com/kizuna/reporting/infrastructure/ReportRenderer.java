package com.kizuna.reporting.infrastructure;

import com.kizuna.reporting.application.ReportBudget;
import com.kizuna.reporting.domain.OperationalReport;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ReportRenderer {
  private static final List<Object> HEADER =
      List.of(
          "行種別",
          "開始営業日",
          "終了営業日",
          "集計単位",
          "生成日時",
          "計算根拠",
          "店舗ID",
          "店舗名",
          "期間・営業日",
          "受注ID",
          "受注版",
          "完了無効化",
          "有効完了件数",
          "無効化件数",
          "請求額（円）",
          "発生済み固定報酬（円）");

  public byte[] render(OperationalReport report, String format, ReportBudget budget)
      throws IOException {
    var output = budget.output();
    if (format.equals("csv")) {
      output.write(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
      writeCsv(output, HEADER, budget);
      rows(report, row -> writeCsv(output, row, budget));
    } else {
      var workbook = new SXSSFWorkbook(100);
      workbook.setCompressTempFiles(true);
      try (workbook) {
        var sheets = new HashMap<String, Sheet>();
        for (String name : List.of("metadata", "summary", "order")) {
          var sheet =
              workbook.createSheet(
                  switch (name) {
                    case "metadata" -> "条件と総計";
                    case "summary" -> "集計";
                    default -> "受注明細";
                  });
          writeXlsx(sheet, HEADER, budget);
          sheet.createFreezePane(0, 1);
          sheets.put(name, sheet);
        }
        rows(report, row -> writeXlsx(sheets.get(row.getFirst().toString()), row, budget));
        budget.check();
        workbook.write(output);
      }
    }
    budget.check();
    return output.toByteArray();
  }

  private void rows(OperationalReport report, RowConsumer rows) throws IOException {
    rows.accept(
        row(
            report,
            "metadata",
            "",
            "",
            "",
            "",
            "",
            "",
            report.totalOrderCount(),
            report.invalidatedOrderCount(),
            report.totalFee(),
            report.totalRemuneration()));
    for (var summary : report.rows())
      rows.accept(
          row(
              report,
              "summary",
              summary.storeId().toString(),
              summary.storeName(),
              summary.period(),
              "",
              "",
              "",
              summary.orderCount(),
              summary.invalidatedOrderCount(),
              summary.totalFee(),
              summary.totalRemuneration()));
    var names = new HashMap<Long, String>();
    for (var store : report.facts().stores()) {
      names.put(store.storeId(), store.storeName());
      rows.accept(
          row(
              report,
              "metadata",
              store.storeId().toString(),
              store.storeName(),
              "",
              "",
              "",
              "",
              "",
              "",
              "",
              ""));
    }
    for (var order : report.facts().orders())
      rows.accept(
          row(
              report,
              "order",
              order.storeId().toString(),
              names.get(order.storeId()),
              order.businessDate().toString(),
              order.orderId(),
              Long.toString(order.version()),
              order.invalidated(),
              order.invalidated() ? 0 : 1,
              order.invalidated() ? 1 : 0,
              order.invalidated() ? 0 : order.totalFee(),
              order.invalidated() ? 0 : order.remuneration()));
  }

  private interface RowConsumer {
    void accept(List<Object> values) throws IOException;
  }

  private List<Object> row(OperationalReport report, String type, Object... values) {
    var row =
        new ArrayList<Object>(
            List.of(
                type,
                report.criteria().from().toString(),
                report.criteria().to().toString(),
                report.criteria().groupBy(),
                report.facts().generatedAt().toString(),
                OperationalReport.BASIS));
    row.addAll(List.of(values));
    return row;
  }

  private void writeCsv(OutputStream output, List<Object> values, ReportBudget budget)
      throws IOException {
    var fields = new ArrayList<String>();
    for (var value : values) {
      String text = value.toString();
      budget.text(text);
      // 文字列は型を持てない CSV の境界で保護し、識別子を数値や式として解釈させない。
      if (value instanceof String && !text.isEmpty()) text = "'" + text;
      fields.add("\"" + text.replace("\"", "\"\"") + "\"");
    }
    output.write((String.join(",", fields) + "\r\n").getBytes(StandardCharsets.UTF_8));
  }

  private void writeXlsx(Sheet sheet, List<Object> values, ReportBudget budget) {
    int index = sheet.getPhysicalNumberOfRows();
    var row = sheet.createRow(index);
    for (int col = 0; col < values.size(); col++) {
      Object value = values.get(col);
      budget.text(value.toString());
      var cell = row.createCell(col);
      if (value instanceof Number number) cell.setCellValue(number.doubleValue());
      else if (value instanceof Boolean bool) cell.setCellValue(bool);
      else cell.setCellValue(value.toString());
    }
  }
}
