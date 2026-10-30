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
          "行種別",
          "生成日時",
          "計算根拠",
          "店舗ID",
          "対象月",
          "広告費登録件数",
          "営業広告登録額（円）",
          "採用広告登録額（円）",
          "広告登録額合計（円）",
          "広告費ID",
          "広告費版",
          "区分",
          "媒体",
          "広告会社",
          "プラン",
          "問い合わせ人数",
          "金額（円）");
  private static final String COSTS = "広告費明細";

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
        var totals = book.createSheet(AdvertisingLabels.CRITERIA);
        var costs = book.createSheet(COSTS);
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
                cost == null ? AdvertisingLabels.CRITERIA : COSTS,
                s.generatedAt().toString(),
                "出力時点の有効な広告費登録（第1版）",
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
              AdvertisingLabels.category(cost.category()),
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
