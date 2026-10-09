package com.kizuna.reporting.infrastructure;

import static com.kizuna.shared.export.TabularCells.writeCsv;
import static com.kizuna.shared.export.TabularCells.writeXlsx;

import com.kizuna.reporting.application.ReportBudget;
import com.kizuna.reporting.domain.AdvertisingAmounts;
import com.kizuna.reporting.domain.OperationalReport;
import java.io.IOException;
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

  private enum ExtraColumn {
    KNOWN_GUARANTEE("保証既知小計（円）"),
    GUARANTEE("保証不足分（円）"),
    BONUS("ボーナス（円）"),
    TOTAL("報酬合計（円）"),
    PENDING_ATTENDANCE("出勤待確認人日数"),
    NOT_CONFIGURED("保証未設定人日数"),
    PERSON("本人ID"),
    TERM("保証条件ID"),
    EFFECTIVE_FROM("条件開始日"),
    STATE("保証条件状態"),
    DAILY_AMOUNT("日額（円）"),
    DURATION("閉合出勤時間"),
    INCOMPLETE("未終了出勤"),
    STATUS("保証判定"),
    AWARD("付与ID"),
    VERSION("付与版"),
    CANCELLED("付与取消");
    private final String label;

    ExtraColumn(String label) {
      this.label = label;
    }

    int index() {
      return HEADER.size() + ordinal();
    }
  }

  private enum AdvertisingColumn {
    STATUS("広告費状態"),
    ENTRIES("広告費登録件数"),
    SALES("営業広告登録額（円）"),
    RECRUITMENT("採用広告登録額（円）"),
    TOTAL("広告登録額合計（円）"),
    ID("広告費ID"),
    VERSION("広告費版"),
    MONTH("広告費対象月"),
    CATEGORY("広告費区分"),
    AMOUNT("広告費額（円）");
    private final String label;

    AdvertisingColumn(String label) {
      this.label = label;
    }

    int index(OperationalReport report) {
      return HEADER.size()
          + (report.remuneration() == null ? 0 : ExtraColumn.values().length)
          + ordinal();
    }
  }

  private List<Object> header(OperationalReport report) {
    var result = new ArrayList<>(HEADER);
    if (report.remuneration() != null)
      for (var column : ExtraColumn.values()) result.add(column.label);
    if (report.advertising() != null)
      for (var column : AdvertisingColumn.values()) result.add(column.label);
    return result;
  }

  public byte[] render(OperationalReport report, String format, ReportBudget budget)
      throws IOException {
    var output = budget.output();
    if (format.equals("csv")) {
      output.write(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
      writeCsv(output, header(report), budget);
      rows(report, row -> writeCsv(output, row, budget));
    } else {
      // POI 5.5.1 の close は一時ファイルも破棄するため、例外経路も同じ寿命に閉じる。
      try (var workbook = new SXSSFWorkbook(100)) {
        workbook.setCompressTempFiles(true);
        var sheets = new HashMap<String, Sheet>();
        var types = new ArrayList<>(List.of("metadata", "summary", "order"));
        if (report.remuneration() != null) types.addAll(List.of("remuneration_day", "bonus"));
        if (report.advertising() != null) types.add("advertising_cost");
        for (String name : types) {
          var sheet =
              workbook.createSheet(
                  switch (name) {
                    case "metadata" -> "条件と総計";
                    case "summary" -> "集計";
                    case "remuneration_day" -> "日別報酬根拠";
                    case "bonus" -> "ボーナス根拠";
                    case "advertising_cost" -> "広告費根拠";
                    default -> "受注明細";
                  });
          writeXlsx(sheet, header(report), budget);
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
        withAmounts(
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
                report.totalRemuneration()),
            report.remuneration(),
            report,
            report.advertising()));
    for (var summary : report.rows())
      rows.accept(
          withAmounts(
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
                  summary.totalRemuneration()),
              summary.remuneration(),
              report,
              summary.advertising()));
    if (report.advertising() != null)
      rows.accept(row(report, "metadata", "", "", "注記: " + report.advertising().explanation()));
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
    if (report.advertisingFacts() != null && report.advertising().entryCount() != null) {
      for (var cost : report.advertisingFacts().costs()) {
        var values =
            row(
                report,
                "advertising_cost",
                cost.storeId().toString(),
                names.get(cost.storeId()),
                cost.month());
        values.set(AdvertisingColumn.ID.index(report), cost.id());
        values.set(AdvertisingColumn.VERSION.index(report), Long.toString(cost.version()));
        values.set(AdvertisingColumn.MONTH.index(report), cost.month());
        values.set(AdvertisingColumn.CATEGORY.index(report), cost.category());
        values.set(AdvertisingColumn.AMOUNT.index(report), cost.amount());
        rows.accept(values);
      }
    }
    if (report.remunerationFacts() != null) {
      for (var day : report.remunerationFacts().days()) {
        var values =
            row(
                report,
                "remuneration_day",
                day.storeId().toString(),
                names.get(day.storeId()),
                day.businessDate().toString(),
                "",
                "",
                "",
                "",
                "",
                "",
                day.orderAmount());
        values.set(
            ExtraColumn.KNOWN_GUARANTEE.index(),
            day.guaranteeAmount() == null ? 0L : day.guaranteeAmount());
        values.set(ExtraColumn.GUARANTEE.index(), blank(day.guaranteeAmount()));
        values.set(ExtraColumn.BONUS.index(), day.bonusAmount());
        values.set(
            ExtraColumn.PENDING_ATTENDANCE.index(),
            day.guaranteeStatus().equals("PENDING_ATTENDANCE") ? 1L : 0L);
        values.set(
            ExtraColumn.NOT_CONFIGURED.index(),
            day.guaranteeStatus().equals("NOT_CONFIGURED") ? 1L : 0L);
        values.set(ExtraColumn.PERSON.index(), day.personId().toString());
        values.set(ExtraColumn.TERM.index(), blank(day.termId()));
        values.set(
            ExtraColumn.EFFECTIVE_FROM.index(),
            day.effectiveFrom() == null ? "" : day.effectiveFrom().toString());
        values.set(ExtraColumn.STATE.index(), blank(day.guaranteeState()));
        values.set(ExtraColumn.DAILY_AMOUNT.index(), blank(day.dailyAmount()));
        values.set(ExtraColumn.DURATION.index(), day.closedDuration());
        values.set(ExtraColumn.INCOMPLETE.index(), day.attendanceIncomplete());
        values.set(ExtraColumn.STATUS.index(), day.guaranteeStatus());
        rows.accept(values);
      }
      for (var bonus : report.remunerationFacts().bonuses()) {
        var values =
            row(
                report,
                "bonus",
                bonus.storeId().toString(),
                names.get(bonus.storeId()),
                bonus.awardDate().toString(),
                "",
                "",
                "",
                "",
                "",
                "",
                "");
        values.set(ExtraColumn.BONUS.index(), bonus.effectiveAmount());
        values.set(ExtraColumn.PERSON.index(), bonus.personId().toString());
        values.set(ExtraColumn.AWARD.index(), bonus.id());
        values.set(ExtraColumn.VERSION.index(), Long.toString(bonus.version()));
        values.set(ExtraColumn.CANCELLED.index(), bonus.cancelled());
        rows.accept(values);
      }
    }
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
                report.basis()));
    for (var value : values) row.add(blank(value));
    while (row.size() < header(report).size()) row.add("");
    return row;
  }

  private Object blank(Object value) {
    return value == null ? "" : value;
  }

  private List<Object> withAmounts(
      List<Object> row,
      OperationalReport.Amounts amounts,
      OperationalReport report,
      AdvertisingAmounts advertising) {
    if (amounts != null) {
      row.set(ExtraColumn.KNOWN_GUARANTEE.index(), amounts.knownGuaranteeTotal());
      row.set(ExtraColumn.GUARANTEE.index(), blank(amounts.guaranteeTotal()));
      row.set(ExtraColumn.BONUS.index(), amounts.bonusTotal());
      row.set(ExtraColumn.TOTAL.index(), blank(amounts.total()));
      row.set(ExtraColumn.PENDING_ATTENDANCE.index(), amounts.pendingAttendanceDays());
      row.set(ExtraColumn.NOT_CONFIGURED.index(), amounts.notConfiguredDays());
    }
    if (advertising != null) {
      row.set(AdvertisingColumn.STATUS.index(report), advertising.status());
      row.set(AdvertisingColumn.ENTRIES.index(report), blank(advertising.entryCount()));
      row.set(AdvertisingColumn.SALES.index(report), blank(advertising.salesAmount()));
      row.set(AdvertisingColumn.RECRUITMENT.index(report), blank(advertising.recruitmentAmount()));
      row.set(AdvertisingColumn.TOTAL.index(report), blank(advertising.recordedTotalAmount()));
    }
    return row;
  }
}
