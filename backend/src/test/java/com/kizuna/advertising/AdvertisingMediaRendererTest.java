package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.advertising.api.dto.AdvertisingMediaSummaryResponse;
import com.kizuna.advertising.application.AdvertisingMediaService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.advertising.domain.AdvertisingMediaReport.Entry;
import com.kizuna.advertising.infrastructure.AdvertisingMediaRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.export.DocumentBudget;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class AdvertisingMediaRendererTest {
  AdvertisingMediaRenderer renderer = new AdvertisingMediaRenderer();

  Snapshot snapshot(List<Entry> entries) {
    return new Snapshot(
        1,
        "2026-10",
        3,
        OffsetDateTime.parse("2026-10-09T12:00:00Z"),
        AdvertisingMediaReport.aggregate(entries));
  }

  DocumentBudget budget() {
    return new DocumentBudget(new AppProperties.OperationalReport());
  }

  @Test
  void exportIncludesAllGroupsBeyondTheDisplayedPageAndPreservesTypes() throws Exception {
    var entries =
        IntStream.range(0, 2001)
            .mapToObj(
                i ->
                    new Entry(
                        AdvertisingCategory.SALES,
                        String.format("%05d", i),
                        100,
                        i == 0 ? null : 0L))
            .toList();
    var report = snapshot(entries);
    var page = AdvertisingMediaSummaryResponse.of(report, PageRequest.of(1, 20));
    assertThat(page.rows().getContent()).hasSize(20);
    assertThat(page.rows().getTotalElements()).isEqualTo(2001);
    assertThat(page.recordedTotalAmount()).isEqualTo(200100);
    assertThat(
            AdvertisingMediaSummaryResponse.of(report, PageRequest.of(Integer.MAX_VALUE, 100))
                .rows())
        .isEmpty();
    String csv = new String(renderer.render(report, "csv", budget()), StandardCharsets.UTF_8);
    assertThat(csv)
        .startsWith("\ufeff")
        .contains("'00000", "'01000", "'02000", "MANUAL_RECORDED_SUM_NOT_DEDUPLICATED");
    assertThat(csv.split("\r\n")).hasSize(2005);
    try (var book =
        new XSSFWorkbook(new ByteArrayInputStream(renderer.render(report, "xlsx", budget())))) {
      assertThat(book.getNumberOfSheets()).isEqualTo(2);
      var sheet = book.getSheet("媒体別集計");
      assertThat(sheet.getLastRowNum()).isEqualTo(2001);
      assertThat(sheet.getRow(1).getCell(9).getStringCellValue()).isEqualTo("00000");
      assertThat(sheet.getRow(1001).getCell(9).getStringCellValue()).isEqualTo("01000");
      assertThat(sheet.getRow(2001).getCell(9).getStringCellValue()).isEqualTo("02000");
      assertThat(sheet.getRow(1).getCell(14).getStringCellValue()).isEmpty();
      assertThat(sheet.getRow(2).getCell(14).getNumericCellValue()).isZero();
      assertThat(book.getSheet("条件と総計").getRow(1).getCell(11).getNumericCellValue())
          .isEqualTo(200100);
      for (var s : book)
        for (var row : s)
          for (var cell : row) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
    }
  }

  @Test
  void protectsTextAndRejectsPrecisionAndBudgetOverflow() throws Exception {
    var report = snapshot(List.of(new Entry(AdvertisingCategory.SALES, "=媒体,\"試験\"", 100, null)));
    assertThat(new String(renderer.render(report, "csv", budget()), StandardCharsets.UTF_8))
        .contains("'=媒体,\"\"試験\"\"");
    var huge =
        snapshot(List.of(new Entry(AdvertisingCategory.SALES, "媒体", 1_000_000_000_000_000L, 0L)));
    assertThatThrownBy(() -> renderer.render(huge, "xlsx", budget()))
        .isInstanceOf(ServiceUnavailableException.class);
    var settings = new AppProperties.OperationalReport();
    settings.setMaxBytes(20);
    assertThatThrownBy(() -> renderer.render(report, "csv", new DocumentBudget(settings)))
        .isInstanceOf(ServiceUnavailableException.class);
    settings.setMaxBytes(100000);
    settings.setMaxCharacters(20);
    assertThatThrownBy(() -> renderer.render(report, "xlsx", new DocumentBudget(settings)))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThat(renderer.render(snapshot(List.of()), "xlsx", budget())).isNotEmpty();
  }
}
