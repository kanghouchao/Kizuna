package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.advertising.api.dto.AdvertisingResponses.CostResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.MonthResponse;
import com.kizuna.advertising.application.AdvertisingService.ExportSnapshot;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.infrastructure.AdvertisingRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.export.DocumentBudget;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AdvertisingRendererTest {
  AdvertisingRenderer renderer = new AdvertisingRenderer();

  ExportSnapshot snapshot(long amount, int count) {
    var at = OffsetDateTime.parse("2026-10-09T12:00:00Z");
    var costs =
        IntStream.range(0, count)
            .mapToObj(
                i ->
                    new CostResponse(
                        String.format("%05d", i),
                        1L,
                        "2026-09",
                        AdvertisingCategory.SALES,
                        "=媒体,\"試験\"",
                        null,
                        "00001",
                        i == 0 ? null : 0,
                        100,
                        0,
                        at,
                        at))
            .toList();
    return new ExportSnapshot(
        new MonthResponse(1L, "2026-09", 1, count, amount, 0, amount, at), costs);
  }

  @Test
  void exportsEveryRowAndProtectsStringsAndNulls() throws Exception {
    var report = snapshot(200100, 2001);
    var bytes =
        renderer.render(report, "csv", new DocumentBudget(new AppProperties.OperationalReport()));
    var text = new String(bytes, StandardCharsets.UTF_8);
    assertThat(text).startsWith("\ufeff");
    assertThat(text).contains("'00000", "'01000", "'02000", "'00001", "'=媒体,\"\"試験\"\"");
    assertThat(text.split("\r\n")).hasSize(2003);
    assertThat(text).contains("\"200100\"");
    try (var book =
        new XSSFWorkbook(
            new ByteArrayInputStream(
                renderer.render(
                    report, "xlsx", new DocumentBudget(new AppProperties.OperationalReport()))))) {
      assertThat(book.getNumberOfSheets()).isEqualTo(2);
      var sheet = book.getSheet("広告費明細");
      assertThat(sheet.getLastRowNum()).isEqualTo(2001);
      assertThat(sheet.getRow(1).getCell(9).getStringCellValue()).isEqualTo("00000");
      assertThat(sheet.getRow(1).getCell(12).getCellType()).isEqualTo(CellType.STRING);
      assertThat(sheet.getRow(1).getCell(15).getStringCellValue()).isEmpty();
      assertThat(sheet.getRow(2).getCell(15).getNumericCellValue()).isZero();
      assertThat(sheet.getRow(2001).getCell(16).getNumericCellValue()).isEqualTo(100);
    }
  }

  @Test
  void enforcesExcelPrecisionAndResourceBudgets() throws Exception {
    var huge = snapshot(1_000_000_000_000_000L, 0);
    assertThatThrownBy(
            () ->
                renderer.render(
                    huge, "xlsx", new DocumentBudget(new AppProperties.OperationalReport())))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThat(
            new String(
                renderer.render(
                    huge, "csv", new DocumentBudget(new AppProperties.OperationalReport())),
                StandardCharsets.UTF_8))
        .contains("1000000000000000");
    var settings = new AppProperties.OperationalReport();
    settings.setMaxBytes(20);
    assertThatThrownBy(() -> renderer.render(snapshot(100, 1), "csv", new DocumentBudget(settings)))
        .isInstanceOf(ServiceUnavailableException.class);
    settings.setMaxBytes(100000);
    settings.setMaxCharacters(20);
    assertThatThrownBy(
            () -> renderer.render(snapshot(100, 1), "xlsx", new DocumentBudget(settings)))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThat(
            renderer.render(
                snapshot(0, 0), "xlsx", new DocumentBudget(new AppProperties.OperationalReport())))
        .isNotEmpty();
  }
}
