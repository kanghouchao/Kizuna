package com.kizuna.order.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.order.api.dto.MonthlyRemunerationOrderSummary;
import com.kizuna.order.application.MonthlyPdfBudget;
import com.kizuna.order.application.MonthlyPdfService;
import com.kizuna.order.application.MonthlyPdfSnapshot;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class MonthlyPdfRendererTest {
  private MonthlyPdfSnapshot snapshot(String service) {
    return new MonthlyPdfSnapshot(
        "日本語の長い店舗名".repeat(15),
        "源氏名さくら".repeat(15),
        "2026-09",
        OffsetDateTime.parse("2026-10-01T15:00:00+09:00"),
        123456,
        List.of(
            new MonthlyRemunerationOrderSummary(
                "LONG-ORDER-00001", LocalDate.of(2026, 9, 30), service, 123456, false),
            new MonthlyRemunerationOrderSummary(
                "INVALIDATED-00002", LocalDate.of(2026, 9, 29), "無効サービス", 0, true)));
  }

  @Test
  void japaneseLongRowContinuesAcrossPagesWithoutLosingTail() throws Exception {
    var budget = new MonthlyPdfBudget(new AppProperties.MonthlyPdf());
    var bytes =
        new MonthlyPdfRenderer(new AppProperties())
            .render(snapshot("長い日本語サービス概要".repeat(500) + "末尾の確認文字"), budget);
    Path evidence = Path.of("build/reports/monthly-pdf");
    Files.createDirectories(evidence);
    Files.write(evidence.resolve("long-japanese.pdf"), bytes);
    try (var doc = Loader.loadPDF(bytes)) {
      String text = new PDFTextStripper().getText(doc).replaceAll("\\s", "");
      assertThat(text).contains("末尾の確認文字", "INVALIDATED-00002", "無効化済み（有効報酬0円）", "123,456円");
      assertThat(doc.getNumberOfPages()).isGreaterThan(2);
      var renderer = new PDFRenderer(doc);
      for (int i = 0; i < doc.getNumberOfPages(); i++)
        ImageIO.write(
            renderer.renderImageWithDPI(i, 120),
            "png",
            evidence.resolve("page-" + (i + 1) + ".png").toFile());
    }
  }

  @Test
  void missingGlyphAndOutputOverflowLeaveNoTemporaryFiles() throws Exception {
    List<Path> before;
    try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      before =
          paths.filter(p -> p.getFileName().toString().startsWith("kizuna-monthly-pdf-")).toList();
    }
    assertThatThrownBy(
            () ->
                new MonthlyPdfRenderer(new AppProperties())
                    .render(
                        snapshot("未対応😀"), new MonthlyPdfBudget(new AppProperties.MonthlyPdf())))
        .isInstanceOf(ServiceUnavailableException.class);
    var tiny = new AppProperties.MonthlyPdf();
    tiny.setMaxBytes(1);
    assertThatThrownBy(
            () ->
                new MonthlyPdfRenderer(new AppProperties())
                    .render(snapshot("日本語"), new MonthlyPdfBudget(tiny)))
        .isInstanceOf(ServiceUnavailableException.class);
    var settings = new AppProperties();
    settings.getMonthlyPdf().setScratchMemoryBytes(4096);
    settings.getMonthlyPdf().setMaxScratchBytes(4096);
    var service = new MonthlyPdfService(new MonthlyPdfRenderer(settings), settings);
    assertThatThrownBy(() -> service.generate(budget -> snapshot("日本語")))
        .isInstanceOf(ServiceUnavailableException.class);
    settings.getMonthlyPdf().setScratchMemoryBytes(8 * 1024 * 1024);
    settings.getMonthlyPdf().setMaxScratchBytes(64 * 1024 * 1024);
    assertThat(service.generate(budget -> snapshot("日本語"))).isNotEmpty();
    try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      assertThat(
              paths
                  .filter(p -> p.getFileName().toString().startsWith("kizuna-monthly-pdf-"))
                  .toList())
          .containsExactlyInAnyOrderElementsOf(before);
    }
  }
}
