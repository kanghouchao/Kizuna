package com.kizuna.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.remuneration.reporting.RemunerationReportFacts;
import com.kizuna.reporting.application.ReportBudget;
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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RemunerationReportRenderingTest {
  private static final LocalDate DAY = LocalDate.of(2026, 9, 30);
  private static final long SAFE_INTEGER = 9_007_199_254_740_991L;
  private static final long EXCEL_INTEGER = 999_999_999_999_999L;

  @Test
  void totalsAddSupplementsAndBonusesWithoutCountingOrderRemunerationTwice() {
    var report =
        report(List.of(order(3000)), List.of(day(1, DAY, 3000, 7000L, 2000, "CALCULATED")));
    assertThat(report.totalRemuneration()).isEqualTo(3000);
    assertThat(report.remuneration())
        .isEqualTo(new OperationalReport.Amounts(7000, 7000L, 2000, 12000L, 0, 0));
    assertThat(report.rows().getFirst().remuneration()).isEqualTo(report.remuneration());
  }

  @Test
  void noOrderStoresAndCrossMonthDaysProduceTheirOwnGroups() {
    var criteria = ReportCriteria.parse("2026-09-01", "2026-10-31", "month");
    var report =
        OperationalReport.aggregate(
            criteria,
            facts(List.of()),
            new RemunerationReportFacts(
                List.of(
                    day(1, DAY, 0, 8000L, 0, "CALCULATED"),
                    day(2, DAY.plusDays(1), 0, 0L, 4000, "NOT_ELIGIBLE")),
                List.of()));
    assertThat(report.rows())
        .extracting(OperationalReport.Row::period)
        .containsExactly("2026-09", "2026-10");
    assertThat(report.rows())
        .extracting(OperationalReport.Row::storeName)
        .containsExactly("一号店", "二号店");
    assertThat(report.totalOrderCount()).isZero();
    assertThat(report.remuneration().total()).isEqualTo(12000L);
  }

  @Test
  void unknownTotalsRetainKnownSubtotalAndZeroIsStillComplete() {
    var report =
        report(
            List.of(),
            List.of(
                day(1, DAY, 0, 6000L, 1000, "CALCULATED"),
                day(1, DAY.minusDays(1), 0, null, 2000, "PENDING_ATTENDANCE"),
                day(2, DAY, 0, null, 0, "NOT_CONFIGURED"),
                day(2, DAY.minusDays(1), 0, 0L, 0, "STOPPED")));
    assertThat(report.remuneration())
        .isEqualTo(new OperationalReport.Amounts(6000, null, 3000, null, 1, 1));
    var zero =
        report.rows().stream()
            .filter(row -> row.storeId() == 2 && row.period().equals("2026-09-29"))
            .findFirst()
            .orElseThrow();
    assertThat(zero.remuneration()).isEqualTo(new OperationalReport.Amounts(0, 0L, 0, 0L, 0, 0));
  }

  @Test
  void unsafeIndividualAmountAndCrossComponentOrCrossRowSumsAreRejected() {
    assertThatThrownBy(
            () -> report(List.of(), List.of(day(1, DAY, 0, SAFE_INTEGER + 1, 0, "CALCULATED"))))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThatThrownBy(
            () -> report(List.of(order(1)), List.of(day(1, DAY, 1, SAFE_INTEGER, 0, "CALCULATED"))))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThatThrownBy(
            () ->
                report(
                    List.of(),
                    List.of(
                        day(1, DAY, 0, SAFE_INTEGER, 0, "CALCULATED"),
                        day(2, DAY, 0, 1L, 0, "CALCULATED"))))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThatThrownBy(
            () ->
                report(
                    List.of(),
                    List.of(
                        day(1, DAY, 0, SAFE_INTEGER, 0, "CALCULATED"),
                        day(2, DAY, 0, null, 1, "PENDING_ATTENDANCE"))))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void csvRetainsExactSafeIntegerAndUnknownCellsAreEmptyRatherThanZero() throws Exception {
    var maximum = report(List.of(), List.of(day(1, DAY, 0, SAFE_INTEGER, 0, "CALCULATED")));
    assertThat(new String(render(maximum, "csv"), StandardCharsets.UTF_8))
        .contains("\"9007199254740991\"")
        .doesNotContain("9.007199");
    var report =
        report(
            List.of(),
            List.of(
                day(1, DAY, 0, null, 2000, "NOT_CONFIGURED"), day(2, DAY, 0, 0L, 0, "STOPPED")));
    var lines = new String(render(report, "csv"), StandardCharsets.UTF_8).split("\r\n");
    assertThat(fields(lines[0])).hasSize(33);
    var total = fields(lines[1]);
    assertThat(total[16]).isEqualTo("0");
    assertThat(total[17]).isEmpty();
    assertThat(total[18]).isEqualTo("2000");
    assertThat(total[19]).isEmpty();
    assertThat(total[21]).isEqualTo("1");
    var unknownDay =
        List.of(lines).stream()
            .filter(line -> line.contains("'remuneration_day") && line.contains("'NOT_CONFIGURED"))
            .findFirst()
            .orElseThrow();
    assertThat(fields(unknownDay)[17]).isEmpty();
    assertThat(fields(unknownDay)[23]).isEmpty();
    var stoppedDay =
        List.of(lines).stream()
            .filter(line -> line.contains("'remuneration_day") && line.contains("'STOPPED"))
            .findFirst()
            .orElseThrow();
    assertThat(fields(stoppedDay)[17]).isEqualTo("0");
  }

  @Test
  void xlsxHasFiveSheetsWithUnknownBlankAndKnownZeroNumericCells() throws Exception {
    var report =
        report(
            List.of(),
            List.of(
                day(1, DAY, 0, null, 2000, "NOT_CONFIGURED"), day(2, DAY, 0, 0L, 0, "STOPPED")));
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(render(report, "xlsx")))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
      var total = workbook.getSheet("条件と総計").getRow(1);
      assertThat(total.getCell(17).getStringCellValue()).isEmpty();
      assertThat(total.getCell(19).getStringCellValue()).isEmpty();
      assertThat(total.getCell(18).getNumericCellValue()).isEqualTo(2000);
      var days = workbook.getSheet("日別報酬根拠");
      assertThat(days.getRow(1).getCell(17).getStringCellValue()).isEmpty();
      assertThat(days.getRow(1).getCell(23).getStringCellValue()).isEmpty();
      assertThat(days.getRow(2).getCell(17).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(days.getRow(2).getCell(17).getNumericCellValue()).isZero();
      assertThat(workbook.getExternalLinksTable()).isEmpty();
    }
  }

  @Test
  void xlsxAcceptsFifteenDigitsAndRejectsIndividualAndSummedSixteenDigits() throws Exception {
    var exact = report(List.of(), List.of(day(1, DAY, 0, EXCEL_INTEGER, 0, "CALCULATED")));
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(render(exact, "xlsx")))) {
      assertThat((long) workbook.getSheet("条件と総計").getRow(1).getCell(17).getNumericCellValue())
          .isEqualTo(EXCEL_INTEGER);
    }
    var single = report(List.of(), List.of(day(1, DAY, 0, EXCEL_INTEGER + 1, 0, "CALCULATED")));
    assertThatThrownBy(() -> render(single, "xlsx"))
        .isInstanceOf(ServiceUnavailableException.class);
    var sum =
        report(
            List.of(),
            List.of(
                day(1, DAY, 0, EXCEL_INTEGER, 0, "CALCULATED"),
                day(2, DAY, 0, 1L, 0, "CALCULATED")));
    assertThatThrownBy(() -> render(sum, "xlsx")).isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void xlsxChecksDailyTermEvenWhenSupplementAndReportTotalAreZero() {
    var day =
        new RemunerationReportFacts.Day(
            1L,
            10L,
            DAY,
            0,
            "term",
            DAY,
            "ACTIVE",
            EXCEL_INTEGER + 1,
            "PT0S",
            false,
            "NOT_ELIGIBLE",
            0L,
            0);
    var report = report(List.of(), List.of(day));
    assertThat(report.remuneration().total()).isZero();
    assertThatThrownBy(() -> render(report, "xlsx"))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void bonusProofKeepsVersionsAndCancellationWithoutNamesOrReasons() throws Exception {
    var bonus = new RemunerationReportFacts.Bonus(2L, 10L, "0007", 4, DAY, true, 0);
    var report =
        OperationalReport.aggregate(
            ReportCriteria.parse("2026-09-01", "2026-09-30", "day"),
            facts(List.of()),
            new RemunerationReportFacts(
                List.of(day(2, DAY, 0, 0L, 0, "NOT_ELIGIBLE")), List.of(bonus)));
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(render(report, "xlsx")))) {
      var row = workbook.getSheet("ボーナス根拠").getRow(1);
      assertThat(row.getCell(18).getNumericCellValue()).isZero();
      assertThat(row.getCell(22).getStringCellValue()).isEqualTo("10");
      assertThat(row.getCell(30).getStringCellValue()).isEqualTo("0007");
      assertThat(row.getCell(31).getStringCellValue()).isEqualTo("4");
      assertThat(row.getCell(32).getBooleanCellValue()).isTrue();
    }
  }

  private byte[] render(OperationalReport report, String format) throws Exception {
    return new ReportRenderer()
        .render(report, format, new ReportBudget(new AppProperties().getOperationalReport()));
  }

  private String[] fields(String line) {
    return line.substring(1, line.length() - 1).split("\",\"", -1);
  }

  private OperationalReport report(
      List<OperationalFacts.Order> orders, List<RemunerationReportFacts.Day> days) {
    return OperationalReport.aggregate(
        ReportCriteria.parse("2026-09-01", "2026-09-30", "day"),
        facts(orders),
        new RemunerationReportFacts(days, List.of()));
  }

  private OperationalFacts facts(List<OperationalFacts.Order> orders) {
    return new OperationalFacts(
        OffsetDateTime.parse("2026-10-01T12:00:00+09:00"),
        List.of(new OperationalFacts.Store(1L, "一号店"), new OperationalFacts.Store(2L, "二号店")),
        orders);
  }

  private OperationalFacts.Order order(int amount) {
    return new OperationalFacts.Order("order", 1L, DAY, 1, false, 10000, amount, 10L);
  }

  private RemunerationReportFacts.Day day(
      long store, LocalDate date, long order, Long guarantee, long bonus, String status) {
    return new RemunerationReportFacts.Day(
        store,
        10L,
        date,
        order,
        null,
        null,
        null,
        null,
        "PT4H",
        status.equals("PENDING_ATTENDANCE"),
        status,
        guarantee,
        bonus);
  }
}
