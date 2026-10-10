package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.advertising.api.dto.AdvertisingOrderCostResponse;
import com.kizuna.advertising.application.AdvertisingOrderCostService.Snapshot;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Cost;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Order;
import com.kizuna.advertising.infrastructure.AdvertisingOrderCostRenderer;
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

  DocumentBudget budget() {
    return new DocumentBudget(new AppProperties.OperationalReport());
  }

  @Test
  void exportsEveryGroupBeyondPageAndPreservesDecimalTextNullAndZero() throws Exception {
    var costs =
        IntStream.range(0, 2001)
            .mapToObj(i -> new Cost(String.format("%05d", i), i == 1 ? 0 : 100))
            .toList();
    var report =
        snapshot(
            costs,
            List.of(
                new Order("00001", true),
                new Order("00002", false),
                new Order("注文のみ", false),
                new Order(null, true)));
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
            "NO_COST_RECORDS",
            "NO_VALID_ORDERS",
            "HALF_UP_2_DECIMAL_YEN",
            "同月同名の記録比較であり、実際の獲得費用・広告による因果・利益・ROIを示しません。");
    assertThat(csv.split("\r\n")).hasSize(2004);
    try (var book =
        new XSSFWorkbook(new ByteArrayInputStream(renderer.render(report, "xlsx", budget())))) {
      assertThat(book.getNumberOfSheets()).isEqualTo(2);
      assertThat(book.getSheetAt(0).getRow(1).getCell(17).getStringCellValue())
          .contains("同月同名の記録比較", "因果・利益・ROIを示しません", "0円の有効完了受注");
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
      for (var s : book)
        for (var row : s)
          for (var cell : row) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
    }
  }

  @Test
  void protectsTextPrecisionAndResourceLimitsIncludingEmptyExports() throws Exception {
    var report = snapshot(List.of(new Cost("=媒体,\"試験\"", 100)), List.of());
    assertThat(new String(renderer.render(report, "csv", budget()), StandardCharsets.UTF_8))
        .contains("'=媒体,\"\"試験\"\"");
    var huge =
        snapshot(List.of(new Cost("媒体", 1_000_000_000_000_000L)), List.of(new Order("媒体", false)));
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
    assertThat(renderer.render(snapshot(List.of(), List.of()), "xlsx", budget())).isNotEmpty();
  }
}
