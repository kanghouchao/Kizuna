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
          "行種別",
          "生成日時",
          "計算根拠",
          "媒体の照合",
          "受注の根拠",
          "端数処理",
          "店舗ID",
          "対象月",
          "対象月版",
          "媒体",
          "費用行数",
          "登録営業広告費（円）",
          "有効完了受注件数",
          "うち0円の受注件数",
          "媒体未入力の有効完了受注件数",
          "一件あたり記録費用（円）",
          "算出状況",
          "解釈");
  private static final String MEDIA = "受注あたり記録広告費";

  public byte[] render(Snapshot source, String format, DocumentBudget budget) throws IOException {
    var output = budget.output();
    var criteria = common(source, AdvertisingLabels.CRITERIA);
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
        var totals = book.createSheet(AdvertisingLabels.CRITERIA);
        var media = book.createSheet(MEDIA);
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
            "登録営業広告費 ÷ 有効完了受注件数（第1版）",
            AdvertisingLabels.EXACT_NAME,
            "原営業日が対象月の有効完了受注（0円を含む）",
            "小数第3位を四捨五入（円）",
            source.storeId(),
            source.month(),
            source.monthVersion()));
  }

  private List<Object> media(Snapshot source, AdvertisingOrderCostReport.Row value) {
    var row = common(source, MEDIA);
    row.addAll(
        List.of(
            value.mediaName(),
            value.costEntryCount(),
            value(value.recordedSalesAmount()),
            value.validCompletedOrderCount(),
            value.zeroAmountOrderCount(),
            "",
            value(value.costPerOrder()),
            status(value.calculationStatus()),
            ""));
    return row;
  }

  private Object value(Object nullable) {
    return nullable == null ? "" : nullable;
  }

  private String status(AdvertisingOrderCostReport.Status status) {
    return switch (status) {
      case CALCULATED -> "記録値から算出";
      case NO_COST_RECORDS -> "営業広告費が未登録";
      case NO_VALID_ORDERS -> "有効完了受注が0件";
    };
  }
}
