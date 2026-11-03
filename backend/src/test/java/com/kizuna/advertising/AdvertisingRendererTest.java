package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.advertising.application.AdvertisingRenderer;
import com.kizuna.advertising.application.AdvertisingService.ExportSnapshot;
import com.kizuna.advertising.application.AdvertisingService.ExportedCost;
import com.kizuna.advertising.application.AdvertisingService.MonthTotals;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import com.kizuna.shared.export.ExportCells;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.stream.IntStream;
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
                    new ExportedCost(
                        String.format("%05d", i),
                        0,
                        AdvertisingCategory.SALES,
                        new AdvertisingMedium("900000", "=媒体,\"試験\""),
                        null,
                        "00001",
                        i == 0 ? null : 0,
                        100))
            .toList();
    return new ExportSnapshot(
        new MonthTotals(1L, "2026-09", 1, count, amount, 0, amount, at), costs);
  }

  @Test
  void exportsEveryCostRowAndUsesNoEnglishButId() throws Exception {
    var report = snapshot(200100, 2001);
    var budget = new DocumentBudget(new AppProperties.OperationalReport());
    var csv = new String(renderer.render(report, "csv", budget), StandardCharsets.UTF_8);
    assertThat(csv.split("\r\n")).hasSize(2003);
    assertThat(ExportCells.letterRuns(csv)).containsExactly("ID");
    try (var book =
        new XSSFWorkbook(
            new ByteArrayInputStream(
                renderer.render(
                    report, "xlsx", new DocumentBudget(new AppProperties.OperationalReport()))))) {
      var sheet = book.getSheet("広告費明細");
      assertThat(sheet.getLastRowNum()).isEqualTo(2001);
      assertThat(sheet.getRow(2001).getCell(9).getStringCellValue()).isEqualTo("02000");
    }
  }
}
