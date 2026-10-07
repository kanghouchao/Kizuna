package com.kizuna.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.order.reporting.OperationalReportReader;
import com.kizuna.reporting.application.OperationalReportService;
import com.kizuna.reporting.application.ReportBudget;
import com.kizuna.reporting.domain.OperationalReport;
import com.kizuna.reporting.domain.ReportCriteria;
import com.kizuna.reporting.infrastructure.ReportRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.util.TempFile;
import org.apache.poi.util.TempFileCreationStrategy;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperationalReportTest {
  final OperationalReportReader reader = mock(OperationalReportReader.class);
  final AppProperties properties = new AppProperties();
  final OperationalReportService service =
      new OperationalReportService(reader, new ReportRenderer(), properties);

  void rows(int count) {
    var rows =
        IntStream.range(0, count)
            .mapToObj(
                i ->
                    new OperationalFacts.Order(
                        String.format("%08d", i),
                        1L,
                        LocalDate.of(2026, 9, 30),
                        4L,
                        false,
                        Integer.MAX_VALUE,
                        3000))
            .toList();
    facts(rows);
  }

  void facts(List<OperationalFacts.Order> orders) {
    when(reader.store(any(), any()))
        .thenReturn(
            new OperationalFacts(
                OffsetDateTime.parse("2026-10-01T12:00:00+09:00"),
                List.of(
                    new OperationalFacts.Store(1L, "=日本語,\"店舗\"\r\n@式"),
                    new OperationalFacts.Store(2L, "空店舗")),
                orders));
  }

  @Test
  void fullExportContainsFirstMiddleLastAndSafeTypedCells() throws Exception {
    rows(2001);
    var response = service.view(false, null, "2026-09-01", "2026-10-31", "day", 0, 1);
    assertThat(response.totalOrderCount()).isEqualTo(2001);
    assertThat(response.totalFee()).isEqualTo(2001L * Integer.MAX_VALUE);
    assertThat(response.stores()).hasSize(2);
    var csv = service.export(false, null, "2026-09-01", "2026-10-31", "day", "csv");
    assertThat(new String(csv, StandardCharsets.UTF_8))
        .startsWith("\ufeff")
        .contains("\"'00000000\"", "\"'00001000\"", "\"'00002000\"", "\"'=日本語,\"\"店舗\"\"\r\n@式\"");
    try (var workbook =
        new XSSFWorkbook(
            new ByteArrayInputStream(
                service.export(false, null, "2026-09-01", "2026-10-31", "day", "xlsx")))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
      var orders = workbook.getSheet("受注明細");
      assertThat(orders.getLastRowNum()).isEqualTo(2001);
      assertThat(orders.getRow(1).getCell(9).getStringCellValue()).isEqualTo("00000000");
      assertThat(orders.getRow(2001).getCell(9).getStringCellValue()).isEqualTo("00002000");
      assertThat(workbook.getSheet("条件と総計").getRow(1).getCell(14).getNumericCellValue())
          .isEqualTo(2001L * Integer.MAX_VALUE);
      assertThat(workbook.getExternalLinksTable()).isEmpty();
      for (var sheet : workbook)
        for (var row : sheet)
          for (var cell : row) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
    }
  }

  @Test
  void correctionsAndInvalidationStayInOriginalMonthWithoutCountingTwice() {
    facts(
        List.of(
            new OperationalFacts.Order("a", 1L, LocalDate.of(2026, 9, 30), 8, false, 12000, 5000),
            new OperationalFacts.Order("b", 1L, LocalDate.of(2026, 10, 1), 6, true, 10000, 4000)));
    var result = service.view(false, null, "2026-09-01", "2026-10-31", "month", 0, 20);
    assertThat(result.totalOrderCount()).isEqualTo(1);
    assertThat(result.invalidatedOrderCount()).isEqualTo(1);
    assertThat(result.totalFee()).isEqualTo(12000);
    assertThat(result.totalRemuneration()).isEqualTo(5000);
    assertThat(result.rows().getContent())
        .extracting(row -> row.period())
        .containsExactly("2026-09", "2026-10");
    assertThat(
            service
                .view(false, null, "2026-09-01", "2026-10-31", "store", 0, 20)
                .rows()
                .getTotalElements())
        .isEqualTo(1);
  }

  @Test
  void emptyIncludesStoresAndZeroTotals() {
    rows(0);
    var result = service.view(false, null, "2026-09-01", "2026-09-30", "day", 0, 20);
    assertThat(result.rows()).isEmpty();
    assertThat(result.totalFee()).isZero();
    assertThat(result.stores()).hasSize(2);
  }

  @Test
  void badCriteriaAndPagingAreRejected() {
    for (String date : List.of("0000-01-01", "2026-02-30", "bad", "2026-9-1"))
      assertThatThrownBy(() -> ReportCriteria.parse(date, "2026-12-31", "day"))
          .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> ReportCriteria.parse("2025-01-01", "2026-01-02", "day"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> ReportCriteria.parse("2026-09-02", "2026-09-01", "day"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> ReportCriteria.parse("2026-09-01", "2026-09-30", "invalid"))
        .isInstanceOf(ServiceException.class);
    for (int size : List.of(-1, 0, 101))
      assertThatThrownBy(
              () -> service.view(false, null, "2026-09-01", "2026-09-30", "day", 0, size))
          .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> service.view(false, null, "2026-09-01", "2026-09-30", "day", -1, 20))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> service.export(false, null, "2026-09-01", "2026-09-30", "day", "xls"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void resourceFailuresReleaseSlotAndNeverReturnPartialOutput() throws Exception {
    rows(2);
    properties.getOperationalReport().setMaxOrders(1);
    assertThatThrownBy(() -> service.view(false, null, "2026-09-01", "2026-09-30", "day", 0, 20))
        .isInstanceOf(ServiceUnavailableException.class);
    properties.getOperationalReport().setMaxOrders(20_000);
    properties.getOperationalReport().setMaxBytes(8);
    for (String format : List.of("csv", "xlsx"))
      assertThatThrownBy(
              () -> service.export(false, null, "2026-09-01", "2026-09-30", "day", format))
          .isInstanceOf(ServiceUnavailableException.class);
    properties.getOperationalReport().setMaxBytes(16L * 1024 * 1024);
    assertThat(service.export(false, null, "2026-09-01", "2026-09-30", "day", "csv")).isNotEmpty();
    properties.getOperationalReport().setMaxCharacters(1);
    assertThatThrownBy(() -> service.export(false, null, "2026-09-01", "2026-09-30", "day", "csv"))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void hardLimitAndNumericBoundaryApplyToViewAndExport() {
    properties.getOperationalReport().setMaxOrders(100_001);
    rows(100_001);
    assertThatThrownBy(() -> service.view(false, null, "2026-09-01", "2026-09-30", "day", 0, 20))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThatThrownBy(() -> service.export(false, null, "2026-09-01", "2026-09-30", "day", "csv"))
        .isInstanceOf(ServiceUnavailableException.class);
    facts(List.of(new OperationalFacts.Order("a", 1L, LocalDate.of(2026, 9, 30), 1, false, -1, 0)));
    assertThatThrownBy(() -> service.view(false, null, "2026-09-01", "2026-09-30", "day", 0, 20))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void xlsxDeletesActualTemporaryFilesOnSuccessAndFailures(@TempDir Path directory) {
    rows(2001);
    var created = new ArrayList<Path>();
    var strategy =
        new TempFileCreationStrategy() {
          @Override
          public File createTempFile(String prefix, String suffix) throws IOException {
            var path = Files.createTempFile(directory, prefix, suffix);
            created.add(path);
            return path.toFile();
          }

          @Override
          public File createTempDirectory(String prefix) throws IOException {
            return Files.createTempDirectory(directory, prefix).toFile();
          }
        };
    for (String outcome : List.of("success", "characters", "bytes", "write")) {
      created.clear();
      properties
          .getOperationalReport()
          .setMaxCharacters(outcome.equals("characters") ? 20_000 : 4_000_000);
      properties
          .getOperationalReport()
          .setMaxBytes(outcome.equals("bytes") ? 8 : 16L * 1024 * 1024);
      TempFile.withStrategy(
          strategy,
          () -> {
            if (outcome.equals("success")) {
              assertThatCode(
                      () ->
                          assertThat(
                                  service.export(
                                      false, null, "2026-09-01", "2026-09-30", "day", "xlsx"))
                              .isNotEmpty())
                  .doesNotThrowAnyException();
            } else if (outcome.equals("write")) {
              var output = mock(ByteArrayOutputStream.class);
              doAnswer(
                      invocation -> {
                        throw new IOException("出力先の書き込み失敗");
                      })
                  .when(output)
                  .write(any(byte[].class), anyInt(), anyInt());
              var budget = spy(new ReportBudget(properties.getOperationalReport()));
              doReturn(output).when(budget).output();
              var criteria = ReportCriteria.parse("2026-09-01", "2026-09-30", "day");
              var report =
                  OperationalReport.aggregate(
                      criteria, reader.store(criteria.from(), criteria.to()));
              assertThatThrownBy(() -> new ReportRenderer().render(report, "xlsx", budget))
                  .isInstanceOf(IOException.class);
            } else {
              assertThatThrownBy(
                      () -> service.export(false, null, "2026-09-01", "2026-09-30", "day", "xlsx"))
                  .isInstanceOf(ServiceUnavailableException.class);
            }
            return null;
          });
      assertThat(created)
          .as(outcome)
          .anyMatch(path -> path.getFileName().toString().startsWith("poi-sxssf-sheet"));
      assertThat(created).allSatisfy(path -> assertThat(path).doesNotExist());
      assertThat(directory).isEmptyDirectory();
    }
  }

  @Test
  void largestSupportedTotalRemainsExactInExcel() throws Exception {
    long maximum = 100_000L * Integer.MAX_VALUE;
    var facts =
        new OperationalFacts(
            OffsetDateTime.parse("2026-10-01T12:00:00+09:00"), List.of(), List.of());
    var report =
        new OperationalReport(
            ReportCriteria.parse("2026-09-01", "2026-09-30", "store"),
            facts,
            100_000,
            0,
            maximum,
            maximum,
            List.of());
    var bytes =
        new ReportRenderer()
            .render(report, "xlsx", new ReportBudget(properties.getOperationalReport()));
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      assertThat((long) workbook.getSheetAt(0).getRow(1).getCell(14).getNumericCellValue())
          .isEqualTo(maximum);
      assertThat((long) workbook.getSheetAt(0).getRow(1).getCell(15).getNumericCellValue())
          .isEqualTo(maximum);
    }
  }
}
