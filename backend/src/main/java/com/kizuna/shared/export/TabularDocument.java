package com.kizuna.shared.export;

import static com.kizuna.shared.export.TabularCells.writeCsv;
import static com.kizuna.shared.export.TabularCells.writeXlsx;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/** 名前付きシートの行を、CSV では見出し 1 行の後に全シートを順に連結し、XLSX ではシートごとに見出し行を固定して書く。 */
public final class TabularDocument {
  private TabularDocument() {}

  /** 行は書くときに一度だけ流す。全行を同時に持たないため。 */
  public record Sheet(String name, Stream<List<Object>> rows) {}

  public static byte[] write(
      String format, List<Object> header, List<Sheet> sheets, DocumentBudget budget)
      throws IOException {
    var output = budget.output();
    if (format.equals("csv")) {
      output.write(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
      writeCsv(output, header, budget);
      for (var sheet : sheets)
        for (var row : (Iterable<List<Object>>) sheet.rows()::iterator)
          writeCsv(output, row, budget);
    } else {
      // POI 5.5.1 の close は一時ファイルも破棄するため、例外経路も同じ寿命に閉じる。
      try (var book = new SXSSFWorkbook(100)) {
        book.setCompressTempFiles(true);
        for (var sheet : sheets) {
          var target = book.createSheet(sheet.name());
          writeXlsx(target, header, budget);
          target.createFreezePane(0, 1);
          sheet.rows().forEach(row -> writeXlsx(target, row, budget));
        }
        budget.check();
        book.write(output);
      }
    }
    budget.check();
    return output.toByteArray();
  }
}
