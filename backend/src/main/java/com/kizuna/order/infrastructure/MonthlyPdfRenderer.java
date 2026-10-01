package com.kizuna.order.infrastructure;

import com.kizuna.order.application.MonthlyPdfBudget;
import com.kizuna.order.application.MonthlyPdfSnapshot;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MonthlyPdfRenderer {
  private final AppProperties properties;

  public byte[] render(MonthlyPdfSnapshot snapshot, MonthlyPdfBudget budget) throws IOException {
    Path directory = Files.createTempDirectory("kizuna-monthly-pdf-");
    try {
      var memory =
          MemoryUsageSetting.setupMixed(
                  properties.getMonthlyPdf().getScratchMemoryBytes(),
                  properties.getMonthlyPdf().getMaxScratchBytes())
              .setTempDir(directory.toFile());
      try (var document = new PDDocument(() -> new ScratchFile(memory));
          var fontData = getClass().getResourceAsStream("/fonts/ipaexg/ipaexg.ttf")) {
        if (fontData == null) throw new IOException("日本語フォントが見つかりません");
        var font = PDType0Font.load(document, fontData, true);
        try (var layout = new Layout(document, font, budget, snapshot.month())) {
          layout.start();
          layout.block("店舗: " + snapshot.storeName());
          layout.block("源氏名: " + snapshot.castName());
          layout.block("対象月: " + snapshot.month());
          layout.block(
              "生成日時: "
                  + snapshot
                      .generatedAt()
                      .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss XXX")));
          layout.block("月次報酬合計: " + yen(snapshot.total()));
          layout.block("生成日時点の報酬集計 / 支払済み額ではありません");
          layout.block("全 " + snapshot.orders().size() + " 件");
          layout.tableHeader();
          if (snapshot.orders().isEmpty()) layout.block("対象月の完了受注はありません");
          for (var row : snapshot.orders()) {
            budget.check();
            layout.row(
                List.of(
                    row.businessDate().toString(),
                    row.orderId(),
                    row.serviceSummary() + (row.completionInvalidated() ? "\n無効化済み（有効報酬 0 円）" : ""),
                    yen(row.accruedRemuneration())));
          }
          layout.finish();
        }
        Path output = directory.resolve("statement.pdf");
        try (var file = Files.newOutputStream(output);
            var limited =
                new OutputStream() {
                  private long count;

                  @Override
                  public void write(int value) throws IOException {
                    budget.bytes(++count);
                    file.write(value);
                  }

                  @Override
                  public void write(byte[] bytes, int offset, int length) throws IOException {
                    budget.bytes(count += length);
                    file.write(bytes, offset, length);
                  }
                }) {
          document.save(limited);
        }
        budget.check();
        return Files.readAllBytes(output);
      }
    } catch (IOException ex) {
      // PDFBox は scratch 上限専用の例外型を持たないため、固定版の固有メッセージだけを変換する。
      if ("Maximum allowed scratch file memory exceeded.".equals(ex.getMessage()))
        throw new ServiceUnavailableException("対象明細が大きいため PDF を生成できませんでした");
      throw ex;
    } finally {
      try (var paths = Files.walk(directory)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  private static String yen(long value) {
    return String.format(Locale.JAPAN, "%,d 円", value);
  }

  private static final class Layout implements AutoCloseable {
    private static final float LEFT = 40;
    private static final float BOTTOM = 48;
    private static final float FONT_SIZE = 9;
    private static final float LINE_HEIGHT = 15;
    private static final float[] WIDTHS = {75, 115, 235, 90};
    private final PDDocument document;
    private final PDType0Font font;
    private final MonthlyPdfBudget budget;
    private final String month;
    private PDPageContentStream stream;
    private float y;

    private Layout(PDDocument document, PDType0Font font, MonthlyPdfBudget budget, String month) {
      this.document = document;
      this.font = font;
      this.budget = budget;
      this.month = month;
    }

    private void start() throws IOException {
      close();
      budget.check();
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      stream = new PDPageContentStream(document, page);
      y = PDRectangle.A4.getHeight() - 45;
      text(LEFT, y, "月次給与明細 / " + month);
      y -= 25;
    }

    private void block(String value) throws IOException {
      for (var line : wrap(value, 515)) {
        if (y < BOTTOM) start();
        text(LEFT, y, line);
        y -= LINE_HEIGHT;
      }
      y -= 5;
    }

    private void tableHeader() throws IOException {
      if (y < BOTTOM + 2 * LINE_HEIGHT) start();
      float x = LEFT;
      var titles = List.of("営業日", "受注番号", "サービス概要", "発生済み報酬");
      for (int i = 0; i < titles.size(); i++) {
        text(x, y, titles.get(i));
        x += WIDTHS[i];
      }
      y -= LINE_HEIGHT;
      stream.moveTo(LEFT, y + 7);
      stream.lineTo(LEFT + 515, y + 7);
      stream.stroke();
      y -= 5;
    }

    private void row(List<String> values) throws IOException {
      var columns = new ArrayList<List<String>>();
      int height = 1;
      for (int i = 0; i < values.size(); i++) {
        var lines = wrap(values.get(i), WIDTHS[i] - 7);
        height = Math.max(height, lines.size());
        columns.add(lines);
      }
      for (int line = 0; line < height; line++) {
        if (y < BOTTOM) {
          start();
          tableHeader();
        }
        float x = LEFT;
        for (int col = 0; col < columns.size(); col++) {
          var lines = columns.get(col);
          if (line < lines.size()) text(x, y, lines.get(line));
          x += WIDTHS[col];
        }
        y -= LINE_HEIGHT;
      }
      y -= 6;
    }

    private List<String> wrap(String value, float width) throws IOException {
      var result = new ArrayList<String>();
      var line = new StringBuilder();
      float used = 0;
      for (int index = 0; index < value.length(); ) {
        budget.check();
        int cp = value.codePointAt(index);
        index += Character.charCount(cp);
        if (cp == '\r') continue;
        if (cp == '\n') {
          result.add(line.toString());
          line.setLength(0);
          used = 0;
          continue;
        }
        String glyph = cp == '\t' ? " " : new String(Character.toChars(cp));
        float advance;
        try {
          advance = font.getStringWidth(glyph) * FONT_SIZE / 1000;
        } catch (IllegalArgumentException ex) {
          throw new ServiceUnavailableException("PDF に表示できない文字が含まれています");
        }
        if (used + advance > width && !line.isEmpty()) {
          result.add(line.toString());
          line.setLength(0);
          used = 0;
        }
        line.append(glyph);
        used += advance;
      }
      result.add(line.toString());
      return result;
    }

    private void text(float x, float baseline, String value) throws IOException {
      budget.check();
      stream.beginText();
      stream.setFont(font, FONT_SIZE);
      stream.newLineAtOffset(x, baseline);
      stream.showText(value);
      stream.endText();
    }

    private void finish() throws IOException {
      close();
      int total = document.getNumberOfPages();
      for (int i = 0; i < total; i++) {
        budget.check();
        try (var footer =
            new PDPageContentStream(
                document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
          footer.beginText();
          footer.setFont(font, FONT_SIZE);
          footer.newLineAtOffset(LEFT, 25);
          footer.showText((i + 1) + " / " + total + " ページ");
          footer.endText();
        }
      }
    }

    @Override
    public void close() throws IOException {
      if (stream != null) {
        stream.close();
        stream = null;
      }
    }
  }
}
