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
import org.apache.poi.ss.usermodel.CellType;
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
  void exportsEveryGroupBeyondPageAndPreservesDecimalTextNullAndZero() throws Exception {
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
    assertThat(csv)
        .startsWith("\ufeff")
        .contains(
            "'00000",
            "'01000",
            "'02000",
            "\"'営業広告費が未登録\"",
            "\"'有効完了受注が0件\"",
            "\"'記録値から算出\"",
            "\"'小数第3位を四捨五入（円）\"",
            "\"'一件あたり記録費用（円）\"",
            "同月同媒体の記録比較であり、実際の獲得費用・広告による因果・利益・ROIを示しません。");
    assertThat(csv.split("\r\n")).hasSize(2005);
    assertThat(ExportCells.letterRuns(csv)).containsExactlyInAnyOrder("ID", "ROI");
    try (var book =
        new XSSFWorkbook(new ByteArrayInputStream(renderer.render(report, "xlsx", budget())))) {
      assertThat(book.getNumberOfSheets()).isEqualTo(2);
      assertThat(book.getSheetAt(0).getRow(1).getCell(17).getStringCellValue())
          .contains("同月同媒体の記録比較", "因果・利益・ROIを示しません", "0円の有効完了受注");
      var sheet = book.getSheet("受注あたり記録広告費");
      assertThat(sheet.getLastRowNum()).isEqualTo(2002);
      assertThat(sheet.getRow(1).getCell(9).getStringCellValue()).isEqualTo("00000");
      assertThat(sheet.getRow(1001).getCell(9).getStringCellValue()).isEqualTo("01000");
      assertThat(sheet.getRow(2001).getCell(9).getStringCellValue()).isEqualTo("02000");
      assertThat(sheet.getRow(1).getCell(15).getStringCellValue()).isEmpty();
      assertThat(sheet.getRow(2).getCell(11).getNumericCellValue()).isZero();
      assertThat(sheet.getRow(2).getCell(15).getStringCellValue()).isEqualTo("0.00");
      assertThat(sheet.getRow(2002).getCell(11).getStringCellValue()).isEmpty();
      assertThat(book.getSheetAt(0).getRow(1).getCell(14).getNumericCellValue()).isEqualTo(1);
      var otherRoutes = book.getSheetAt(0).getRow(2);
      assertThat(otherRoutes.getCell(0).getStringCellValue()).isEqualTo("媒体以外の経路");
      assertThat(otherRoutes.getCell(12).getNumericCellValue()).isEqualTo(2);
      assertThat(otherRoutes.getLastCellNum()).isEqualTo((short) 18);
      for (var s : book)
        for (var row : s)
          for (var cell : row) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
    }
  }
}
