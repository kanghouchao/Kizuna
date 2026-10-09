package com.kizuna.shared.export;

import com.kizuna.shared.exception.ServiceUnavailableException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;

public final class TabularCells {
  private TabularCells() {}

  public static void writeCsv(OutputStream output, List<Object> values, DocumentBudget budget)
      throws IOException {
    var fields = new ArrayList<String>();
    for (var value : values) {
      String text = value.toString();
      budget.text(text);
      // 文字列は型を持てない CSV の境界で保護し、識別子を数値や式として解釈させない。
      if (value instanceof String && !text.isEmpty()) text = "'" + text;
      fields.add("\"" + text.replace("\"", "\"\"") + "\"");
    }
    output.write((String.join(",", fields) + "\r\n").getBytes(StandardCharsets.UTF_8));
  }

  public static void writeXlsx(Sheet sheet, List<Object> values, DocumentBudget budget) {
    int index = sheet.getPhysicalNumberOfRows();
    var row = sheet.createRow(index);
    for (int col = 0; col < values.size(); col++) {
      Object value = values.get(col);
      budget.text(value.toString());
      var cell = row.createCell(col);
      if (value instanceof Number number) {
        if (number.longValue() < 0 || number.longValue() > 999_999_999_999_999L)
          throw new ServiceUnavailableException("Excelで正確に表せる金額の上限を超えています。CSVを選ぶか条件を絞ってください");
        cell.setCellValue(number.doubleValue());
      } else if (value instanceof Boolean bool) cell.setCellValue(bool);
      else cell.setCellValue(value.toString());
    }
  }
}
