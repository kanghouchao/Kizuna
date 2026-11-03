package com.kizuna.shared.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.export.TabularDocument.Sheet;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TabularDocumentTest {
  static final List<Object> HEADER = List.of("行種別", "値");

  static DocumentBudget budget() {
    return new DocumentBudget(new AppProperties.OperationalReport());
  }

  static List<Sheet> sheets(Object first, Object second) {
    return List.of(
        new Sheet("条件", Stream.of(List.of("条件", first))),
        new Sheet("明細", Stream.of(List.of("明細", second), List.of("明細", ""))));
  }

  @Test
  void csvConcatenatesEverySheetAfterOneHeaderAndProtectsText() throws Exception {
    var csv =
        new String(
            TabularDocument.write("csv", HEADER, sheets("=式,\"引用\"", 1L), budget()),
            StandardCharsets.UTF_8);
    assertThat(csv)
        .isEqualTo(
            "﻿\"'行種別\",\"'値\"\r\n"
                + "\"'条件\",\"'=式,\"\"引用\"\"\"\r\n"
                + "\"'明細\",\"1\"\r\n"
                + "\"'明細\",\"\"\r\n");
    assertThat(
            new String(
                TabularDocument.write("csv", HEADER, sheets("00123", 1L), budget()),
                StandardCharsets.UTF_8))
        .contains("\"'条件\",\"'00123\"\r\n");
  }

  @Test
  void xlsxWritesOneFrozenHeaderPerSheetAndKeepsCellTypes() throws Exception {
    var bytes = TabularDocument.write("xlsx", HEADER, sheets("00123", 2L), budget());
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      assertThat(book.getNumberOfSheets()).isEqualTo(2);
      assertThat(book.getSheetAt(0).getSheetName()).isEqualTo("条件");
      for (var sheet : book) {
        assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("行種別");
        assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
        for (var row : sheet)
          for (var cell : row) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
      }
      assertThat(book.getSheet("条件").getRow(1).getCell(1).getStringCellValue()).isEqualTo("00123");
      var detail = book.getSheet("明細");
      assertThat(detail.getLastRowNum()).isEqualTo(2);
      assertThat(detail.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(2);
      assertThat(detail.getRow(2).getCell(1).getStringCellValue()).isEmpty();
    }
    assertThat(
            TabularDocument.write(
                "xlsx", HEADER, List.of(new Sheet("空", Stream.empty())), budget()))
        .isNotEmpty();
  }

  @Test
  void rejectsAmountsExcelCannotHoldButKeepsThemInCsv() throws Exception {
    assertThatThrownBy(
            () ->
                TabularDocument.write("xlsx", HEADER, sheets("", 1_000_000_000_000_000L), budget()))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThat(
            new String(
                TabularDocument.write("csv", HEADER, sheets("", 1_000_000_000_000_000L), budget()),
                StandardCharsets.UTF_8))
        .contains("\"1000000000000000\"");
  }

  @Test
  void enforcesTheByteAndCharacterBudgets() {
    var settings = new AppProperties.OperationalReport();
    settings.setMaxBytes(20);
    assertThatThrownBy(
            () ->
                TabularDocument.write("csv", HEADER, sheets("値", 1L), new DocumentBudget(settings)))
        .isInstanceOf(ServiceUnavailableException.class);
    settings.setMaxBytes(100000);
    settings.setMaxCharacters(10);
    assertThatThrownBy(
            () ->
                TabularDocument.write(
                    "xlsx", HEADER, sheets("値", 1L), new DocumentBudget(settings)))
        .isInstanceOf(ServiceUnavailableException.class);
  }
}
