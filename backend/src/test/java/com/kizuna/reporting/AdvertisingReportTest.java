package com.kizuna.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.advertising.reporting.AdvertisingReportFacts;
import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.remuneration.reporting.RemunerationReportFacts;
import com.kizuna.reporting.application.ReportBudget;
import com.kizuna.reporting.domain.AdvertisingAmounts;
import com.kizuna.reporting.domain.OperationalReport;
import com.kizuna.reporting.domain.ReportCriteria;
import com.kizuna.reporting.infrastructure.ReportRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AdvertisingReportTest {
  private static final long SAFE_INTEGER = 9_007_199_254_740_991L;
  private static final long EXCEL_INTEGER = 999_999_999_999_999L;

  @Test
  void monthlyRowsIncludeCostOnlyStoresAndNeverSubtractCostsFromFeesOrRemuneration() {
    var criteria = ReportCriteria.parse("2026-09-01", "2026-10-31", "month");
    var orders =
        List.of(
            new OperationalFacts.Order(
                "order", 1L, LocalDate.of(2026, 9, 30), 1, false, 10000, 3000));
    var report =
        OperationalReport.aggregate(
            criteria,
            facts(orders),
            new RemunerationReportFacts(List.of(), List.of()),
            costs(
                cost(1, "a", "2026-09", "SALES", 8000),
                cost(1, "b", "2026-09", "RECRUITMENT", 500),
                cost(2, "c", "2026-10", "SALES", 4000)));
    assertThat(report.totalFee()).isEqualTo(10000);
    assertThat(report.totalRemuneration()).isEqualTo(3000);
    assertThat(report.remuneration().total()).isEqualTo(3000L);
    assertThat(report.advertising())
        .isEqualTo(new AdvertisingAmounts("RECORDED", 3L, 12000L, 500L, 12500L));
    assertThat(report.rows())
        .extracting(OperationalReport.Row::period)
        .containsExactly("2026-09", "2026-10");
    assertThat(report.rows().getLast().storeId()).isEqualTo(2L);
    assertThat(report.rows().getLast().orderCount()).isZero();
    assertThat(report.rows().getLast().advertising().recordedTotalAmount()).isEqualTo(4000L);
  }

  @Test
  void zeroCostIsRecordedWhileMissingCostIsNoRecordsAndEmptyMonthsAreNotInvented() {
    var orders =
        List.of(
            new OperationalFacts.Order(
                "order", 1L, LocalDate.of(2026, 9, 30), 1, false, 10000, 3000));
    var report =
        OperationalReport.aggregate(
            ReportCriteria.parse("2026-09-01", "2026-10-31", "month"),
            facts(orders),
            null,
            costs(cost(2, "zero", "2026-10", "SALES", 0)));
    assertThat(report.advertising()).isEqualTo(new AdvertisingAmounts("RECORDED", 1L, 0L, 0L, 0L));
    assertThat(report.rows()).hasSize(2);
    assertThat(report.rows().getFirst().advertising())
        .isEqualTo(new AdvertisingAmounts("NO_RECORDS", 0L, 0L, 0L, 0L));
    assertThat(report.rows().getLast().advertising())
        .isEqualTo(new AdvertisingAmounts("RECORDED", 1L, 0L, 0L, 0L));
    var empty = report("2026-09-01", "2026-10-31", "month", costs());
    assertThat(empty.rows()).isEmpty();
    assertThat(empty.facts().stores()).hasSize(2);
    assertThat(empty.advertising().status()).isEqualTo("NO_RECORDS");
  }

  @Test
  void partialMonthsAndDayGroupingHaveExplicitNullWithPartialMonthTakingPrecedence() {
    for (String[] query :
        List.of(
            new String[] {"2026-09-02", "2026-10-31", "month", "NOT_APPLICABLE_PARTIAL_MONTH"},
            new String[] {"2026-09-01", "2026-10-30", "store", "NOT_APPLICABLE_PARTIAL_MONTH"},
            new String[] {"2026-09-02", "2026-10-30", "day", "NOT_APPLICABLE_PARTIAL_MONTH"},
            new String[] {"2026-09-01", "2026-10-31", "day", "NOT_APPLICABLE_DAY_GROUPING"})) {
      var report = report(query[0], query[1], query[2], costs());
      assertThat(report.advertising())
          .isEqualTo(new AdvertisingAmounts(query[3], null, null, null, null));
      assertThat(report.rows()).isEmpty();
    }
  }

  @Test
  void leapYearAndCrossYearCostsKeepSavedMonthAndStoreGroupingSumsAllMonths() {
    var leap =
        report(
            "2028-01-01", "2028-12-31", "month", costs(cost(1, "leap", "2028-02", "SALES", 200)));
    assertThat(leap.rows().getFirst().period()).isEqualTo("2028-02");
    assertThat(leap.advertising().status()).isEqualTo("RECORDED");
    var cross =
        report(
            "2026-12-01",
            "2027-01-31",
            "store",
            costs(
                cost(1, "a", "2026-12", "SALES", 100),
                cost(1, "b", "2027-01", "RECRUITMENT", 200)));
    assertThat(cross.rows()).hasSize(1);
    assertThat(cross.rows().getFirst().advertising())
        .isEqualTo(new AdvertisingAmounts("RECORDED", 2L, 100L, 200L, 300L));
  }

  @Test
  void currentCorrectionAndDeletionRecomputeOriginalMonth() {
    var original =
        report("2026-09-01", "2026-09-30", "month", costs(cost(1, "a", "2026-09", "SALES", 100)));
    var corrected =
        report(
            "2026-09-01",
            "2026-09-30",
            "month",
            costs(new AdvertisingReportFacts.Cost(1L, "a", 2, "2026-09", "RECRUITMENT", 300)));
    var deleted = report("2026-09-01", "2026-09-30", "month", costs());
    assertThat(original.advertising().recordedTotalAmount()).isEqualTo(100L);
    assertThat(corrected.advertising())
        .isEqualTo(new AdvertisingAmounts("RECORDED", 1L, 0L, 300L, 300L));
    assertThat(corrected.rows().getFirst().period()).isEqualTo("2026-09");
    assertThat(deleted.advertising().status()).isEqualTo("NO_RECORDS");
  }

  @Test
  void unsafeIndividualAndCrossCategoryAndCrossStoreSumsFailWithoutRounding() {
    for (var costs :
        List.of(
            costs(cost(1, "a", "2026-09", "SALES", -1)),
            costs(cost(1, "a", "2026-09", "SALES", SAFE_INTEGER + 1)),
            costs(
                cost(1, "a", "2026-09", "SALES", SAFE_INTEGER),
                cost(1, "b", "2026-09", "RECRUITMENT", 1)),
            costs(
                cost(1, "a", "2026-09", "SALES", SAFE_INTEGER),
                cost(2, "b", "2026-09", "SALES", 1)))) {
      assertThatThrownBy(() -> report("2026-09-01", "2026-09-30", "month", costs))
          .isInstanceOf(ServiceUnavailableException.class);
    }
  }

  @Test
  void eachOptInPreservesLegacyColumnsAndSheetsAndUsesDistinctBasis() throws Exception {
    var criteria = ReportCriteria.parse("2026-09-01", "2026-09-30", "month");
    for (boolean remuneration : List.of(false, true)) {
      for (boolean advertising : List.of(false, true)) {
        var report =
            OperationalReport.aggregate(
                criteria,
                facts(List.of()),
                remuneration ? new RemunerationReportFacts(List.of(), List.of()) : null,
                advertising ? costs() : null);
        int columns = 16 + (remuneration ? 17 : 0) + (advertising ? 10 : 0);
        int sheets = 3 + (remuneration ? 2 : 0) + (advertising ? 1 : 0);
        var csv = new String(render(report, "csv"), StandardCharsets.UTF_8);
        assertThat(fields(csv.split("\r\n")[0])).hasSize(columns);
        assertThat(report.basis())
            .isEqualTo(
                advertising
                    ? (remuneration
                        ? "completed-orders-remuneration-advertising-current-v3"
                        : "completed-orders-advertising-current-v3")
                    : (remuneration
                        ? "completed-orders-remuneration-current-v2"
                        : "completed-orders-current-v1"));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(render(report, "xlsx")))) {
          assertThat(workbook.getNumberOfSheets()).isEqualTo(sheets);
          assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short) columns);
        }
      }
    }
  }

  @Test
  void fullCostExportKeepsAllRowsVersionsAndCsvFormulaProtection() throws Exception {
    var costs =
        new AdvertisingReportFacts(
            IntStream.range(0, 2001)
                .mapToObj(i -> cost(1, String.format("=cost%04d", i), "2026-09", "SALES", i))
                .toList());
    var report = report("2026-09-01", "2026-09-30", "month", costs);
    var csv = new String(render(report, "csv"), StandardCharsets.UTF_8);
    assertThat(csv)
        .startsWith("\ufeff")
        .contains("\"'=cost0000\"", "\"'=cost1000\"", "\"'=cost2000\"");
    assertThat(csv.lines().filter(line -> line.contains("'advertising_cost"))).hasSize(2001);
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(render(report, "xlsx")))) {
      var sheet = workbook.getSheet("広告費根拠");
      assertThat(sheet.getLastRowNum()).isEqualTo(2001);
      assertThat(sheet.getRow(1).getCell(21).getStringCellValue()).isEqualTo("=cost0000");
      assertThat(sheet.getRow(2001).getCell(21).getStringCellValue()).isEqualTo("=cost2000");
      assertThat(sheet.getRow(2001).getCell(22).getStringCellValue()).isEqualTo("1");
      assertThat(sheet.getRow(2001).getCell(23).getStringCellValue()).isEqualTo("2026-09");
      assertThat(sheet.getRow(2001).getCell(24).getStringCellValue()).isEqualTo("SALES");
      assertThat(sheet.getRow(2001).getCell(25).getNumericCellValue()).isEqualTo(2000);
      assertThat(sheet.getRow(1).getCell(20).getStringCellValue()).isEmpty();
      assertThat(workbook.getExternalLinksTable()).isEmpty();
      for (var page : workbook)
        for (var row : page)
          for (var cell : row) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
    }
  }

  @Test
  void notApplicableExportsBlankAmountsAndAnEmptyProofSheet() throws Exception {
    var report = report("2026-09-02", "2026-09-30", "month", costs());
    var csv = new String(render(report, "csv"), StandardCharsets.UTF_8);
    var total = fields(csv.split("\r\n")[1]);
    assertThat(total[16]).isEqualTo("'NOT_APPLICABLE_PARTIAL_MONTH");
    for (int i = 17; i <= 20; i++) assertThat(total[i]).isEmpty();
    assertThat(csv).doesNotContain("'advertising_cost");
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(render(report, "xlsx")))) {
      assertThat(workbook.getSheet("広告費根拠").getLastRowNum()).isZero();
      assertThat(workbook.getSheet("条件と総計").getRow(1).getCell(20).getStringCellValue()).isEmpty();
    }
  }

  @Test
  void exactCsvSafeIntegerAndExcelFifteenDigitBoundaryAreIndependent() throws Exception {
    var safe =
        report(
            "2026-09-01",
            "2026-09-30",
            "month",
            costs(cost(1, "a", "2026-09", "SALES", SAFE_INTEGER)));
    assertThat(new String(render(safe, "csv"), StandardCharsets.UTF_8))
        .contains("\"9007199254740991\"")
        .doesNotContain("9.007199");
    var exact =
        report(
            "2026-09-01",
            "2026-09-30",
            "month",
            costs(cost(1, "a", "2026-09", "SALES", EXCEL_INTEGER)));
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(render(exact, "xlsx")))) {
      assertThat((long) workbook.getSheet("条件と総計").getRow(1).getCell(20).getNumericCellValue())
          .isEqualTo(EXCEL_INTEGER);
    }
    for (var costs :
        List.of(
            costs(cost(1, "a", "2026-09", "SALES", EXCEL_INTEGER + 1)),
            costs(
                cost(1, "a", "2026-09", "SALES", EXCEL_INTEGER),
                cost(2, "b", "2026-09", "SALES", 1)))) {
      var report = report("2026-09-01", "2026-09-30", "month", costs);
      assertThatThrownBy(() -> render(report, "xlsx"))
          .isInstanceOf(ServiceUnavailableException.class);
    }
  }

  private byte[] render(OperationalReport report, String format) throws Exception {
    return new ReportRenderer()
        .render(report, format, new ReportBudget(new AppProperties().getOperationalReport()));
  }

  private String[] fields(String line) {
    if (line.startsWith("\ufeff")) line = line.substring(1);
    return line.substring(1, line.length() - 1).split("\",\"", -1);
  }

  private OperationalReport report(
      String from, String to, String group, AdvertisingReportFacts costs) {
    return OperationalReport.aggregate(
        ReportCriteria.parse(from, to, group), facts(List.of()), null, costs);
  }

  private AdvertisingReportFacts costs(AdvertisingReportFacts.Cost... costs) {
    return new AdvertisingReportFacts(List.of(costs));
  }

  private AdvertisingReportFacts.Cost cost(
      long store, String id, String month, String category, long amount) {
    return new AdvertisingReportFacts.Cost(store, id, 1, month, category, amount);
  }

  private OperationalFacts facts(List<OperationalFacts.Order> orders) {
    return new OperationalFacts(
        OffsetDateTime.parse("2026-10-01T12:00:00+09:00"),
        List.of(new OperationalFacts.Store(1L, "一号店"), new OperationalFacts.Store(2L, "二号店")),
        orders);
  }
}
