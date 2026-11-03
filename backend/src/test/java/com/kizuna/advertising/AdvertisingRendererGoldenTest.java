package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.advertising.application.AdvertisingMediaRenderer;
import com.kizuna.advertising.application.AdvertisingMediaService;
import com.kizuna.advertising.application.AdvertisingOrderCostRenderer;
import com.kizuna.advertising.application.AdvertisingOrderCostService;
import com.kizuna.advertising.application.AdvertisingRenderer;
import com.kizuna.advertising.application.AdvertisingService.ExportSnapshot;
import com.kizuna.advertising.application.AdvertisingService.ExportedCost;
import com.kizuna.advertising.application.AdvertisingService.MonthTotals;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.advertising.domain.AdvertisingMediaReport.Entry;
import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Cost;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Order;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 三つの帳票の出力を固定入力の golden と比べる（CSV は字節、XLSX はシート名・固定行・全セルの型と値）。 作り直しは backend/ で
 * KIZUNA_WRITE_GOLDEN=1 ./gradlew test --tests '*AdvertisingRendererGoldenTest' を流して
 * src/test/resources/advertising/golden/ を上書きし、差分を読んでからコミットする。出力を変える意図の無い変更では作り直さない。
 */
class AdvertisingRendererGoldenTest {
  private static final Path SOURCE = Path.of("src/test/resources/advertising/golden");
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-09T12:00:00Z");

  @FunctionalInterface
  interface Render {
    byte[] render(String format) throws IOException;
  }

  static List<Arguments> documents() {
    var costs = new AdvertisingRenderer();
    var media = new AdvertisingMediaRenderer();
    var orderCosts = new AdvertisingOrderCostRenderer();
    return List.of(
        Arguments.of("costs", (Render) f -> costs.render(costs(), f, budget())),
        Arguments.of("costs-empty", (Render) f -> costs.render(emptyCosts(), f, budget())),
        Arguments.of("media", (Render) f -> media.render(media(), f, budget())),
        Arguments.of("media-empty", (Render) f -> media.render(emptyMedia(), f, budget())),
        Arguments.of("order-costs", (Render) f -> orderCosts.render(orderCosts(), f, budget())),
        Arguments.of(
            "order-costs-empty", (Render) f -> orderCosts.render(emptyOrderCosts(), f, budget())));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("documents")
  void csvMatchesTheGoldenByteForByte(String name, Render render) throws Exception {
    assertGolden(name + ".csv", render.render("csv"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("documents")
  void xlsxMatchesTheGoldenCellByCell(String name, Render render) throws Exception {
    assertGolden(name + ".xlsx.txt", cells(render.render("xlsx")));
  }

  private static void assertGolden(String file, byte[] actual) throws IOException {
    if ("1".equals(System.getenv("KIZUNA_WRITE_GOLDEN"))) {
      Files.createDirectories(SOURCE);
      Files.write(SOURCE.resolve(file), actual);
      return;
    }
    byte[] golden;
    try (InputStream in =
        AdvertisingRendererGoldenTest.class.getResourceAsStream("/advertising/golden/" + file)) {
      assertThat(in).as(file).isNotNull();
      golden = in.readAllBytes();
    }
    // 文字列で先に比べるのは、食い違いを行単位の差分で読めるようにするため。
    assertThat(new String(actual, StandardCharsets.UTF_8))
        .as(file)
        .isEqualTo(new String(golden, StandardCharsets.UTF_8));
    assertThat(actual).as(file).isEqualTo(golden);
  }

  // POI は作成日時を書くので字節では比べず、セルを一つずつ文字にする。
  private static byte[] cells(byte[] xlsx) throws IOException {
    var out = new StringBuilder();
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      for (var sheet : book) {
        var pane = sheet.getPaneInformation();
        out.append("sheet\t")
            .append(sheet.getSheetName())
            .append("\tfrozen-rows=")
            .append(pane == null ? 0 : pane.getHorizontalSplitPosition())
            .append("\trows=")
            .append(sheet.getPhysicalNumberOfRows())
            .append('\n');
        for (var row : sheet)
          for (var cell : row)
            out.append(cell.getRowIndex())
                .append(',')
                .append(cell.getColumnIndex())
                .append('\t')
                .append(cell.getCellType())
                .append('\t')
                .append(
                    switch (cell.getCellType()) {
                      case NUMERIC ->
                          BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
                      case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                      default -> cell.getStringCellValue();
                    })
                .append('\n');
      }
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static DocumentBudget budget() {
    return new DocumentBudget(new AppProperties.OperationalReport());
  }

  private static ExportedCost cost(
      String id,
      AdvertisingCategory category,
      String mediumName,
      String agency,
      String plan,
      Integer inquiryCount,
      int amount) {
    return new ExportedCost(
        id,
        3,
        category,
        new AdvertisingMedium("m-" + id, mediumName),
        agency,
        plan,
        inquiryCount,
        amount);
  }

  private static ExportSnapshot costs() {
    return new ExportSnapshot(
        new MonthTotals(7L, "2026-09", 4, 4, 2600, 1000, 3600, AT),
        List.of(
            cost("00001", AdvertisingCategory.SALES, "=媒体,\"試験\"", null, "上位枠", null, 100),
            cost("00002", AdvertisingCategory.SALES, "00123", "代理店A", null, 0, 0),
            cost("00003", AdvertisingCategory.SALES, "+81-3", "", "", 12, 2500),
            cost("00004", AdvertisingCategory.RECRUITMENT, "求人誌", "代理店B", "通年", 3, 1000)));
  }

  private static ExportSnapshot emptyCosts() {
    return new ExportSnapshot(new MonthTotals(7L, "2026-10", 0, 0, 0, 0, 0, AT), List.of());
  }

  private static AdvertisingMedium medium(String id, String name) {
    return new AdvertisingMedium(id, name);
  }

  private static AdvertisingMediaService.Snapshot media() {
    var formula = medium("m1", "=媒体,\"試験\"");
    return new AdvertisingMediaService.Snapshot(
        7,
        "2026-09",
        4,
        AT,
        AdvertisingMediaReport.aggregate(
            List.of(
                new Entry(AdvertisingCategory.SALES, formula, 100, null),
                new Entry(AdvertisingCategory.SALES, formula, 200, 0L),
                new Entry(AdvertisingCategory.SALES, medium("m2", "00123"), 0, 0L),
                new Entry(AdvertisingCategory.SALES, medium("m3", "+81-3"), 2500, 12L),
                new Entry(AdvertisingCategory.RECRUITMENT, medium("m4", "求人誌"), 1000, null))));
  }

  private static AdvertisingMediaService.Snapshot emptyMedia() {
    return new AdvertisingMediaService.Snapshot(
        7, "2026-10", 0, AT, AdvertisingMediaReport.aggregate(List.of()));
  }

  private static AdvertisingOrderCostService.Snapshot orderCosts() {
    var formula = medium("m1", "=媒体,\"試験\"");
    var digits = medium("m2", "00123");
    return new AdvertisingOrderCostService.Snapshot(
        7,
        "2026-09",
        4,
        AT,
        AdvertisingOrderCostReport.aggregate(
            List.of(
                new Cost(formula, 1000), new Cost(digits, 0), new Cost(medium("m3", "+81-3"), 333)),
            List.of(
                new Order(formula, false, false),
                new Order(formula, false, true),
                new Order(formula, false, false),
                new Order(digits, false, false),
                new Order(medium("m5", "受注のみ"), false, false),
                new Order(null, true, false),
                new Order(null, false, true))));
  }

  private static AdvertisingOrderCostService.Snapshot emptyOrderCosts() {
    return new AdvertisingOrderCostService.Snapshot(
        7, "2026-10", 0, AT, AdvertisingOrderCostReport.aggregate(List.of(), List.of()));
  }
}
