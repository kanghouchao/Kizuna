package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.advertising.api.dto.AdvertisingOrderCostResponse;
import com.kizuna.advertising.application.AdvertisingOrderCostRenderer;
import com.kizuna.advertising.application.AdvertisingOrderCostService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Cost;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Order;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import com.kizuna.shared.export.ExportCells;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class AdvertisingOrderCostRendererTest {
  AdvertisingOrderCostRenderer renderer = new AdvertisingOrderCostRenderer();

  Snapshot snapshot(List<Cost> costs, List<Order> orders) {
    return new Snapshot(
        1,
        "2026-10",
        3,
        OffsetDateTime.parse("2026-10-09T12:00:00Z"),
        AdvertisingOrderCostReport.aggregate(costs, orders));
  }

  /** id は表示されない数字列にして、出力の媒体列が現在名であることを区別する。 */
  static AdvertisingMedium medium(String name) {
    return new AdvertisingMedium("9" + name, name);
  }

  DocumentBudget budget() {
    return new DocumentBudget(new AppProperties.OperationalReport());
  }

  @Test
  void exportsEveryGroupBeyondThePage() throws Exception {
    var costs =
        IntStream.range(0, 2001)
            .mapToObj(i -> new Cost(medium(String.format("%05d", i)), i == 1 ? 0 : 100))
            .toList();
    var report =
        snapshot(
            costs,
            List.of(
                new Order(medium("00001"), false, true),
                new Order(medium("00002"), false, false),
                new Order(medium("注文のみ"), false, false),
                new Order(null, false, true),
                new Order(null, true, false),
                new Order(null, true, true)));
    var page = AdvertisingOrderCostResponse.of(report, PageRequest.of(1, 20));
    assertThat(page.rows().getContent()).hasSize(20);
    assertThat(page.rows().getTotalElements()).isEqualTo(2002);
    assertThat(
            AdvertisingOrderCostResponse.of(report, PageRequest.of(Integer.MAX_VALUE, 100)).rows())
        .isEmpty();
    String csv = new String(renderer.render(report, "csv", budget()), StandardCharsets.UTF_8);
    assertThat(csv.split("\r\n")).hasSize(2005);
    assertThat(ExportCells.letterRuns(csv)).containsExactlyInAnyOrder("ID", "ROI");
    try (var book =
        new XSSFWorkbook(new ByteArrayInputStream(renderer.render(report, "xlsx", budget())))) {
      var sheet = book.getSheet("受注あたり記録広告費");
      assertThat(sheet.getLastRowNum()).isEqualTo(2002);
      assertThat(sheet.getRow(1001).getCell(9).getStringCellValue()).isEqualTo("01000");
      assertThat(sheet.getRow(2001).getCell(9).getStringCellValue()).isEqualTo("02000");
      assertThat(sheet.getRow(2001).getCell(11).getNumericCellValue()).isEqualTo(100);
    }
  }
}
