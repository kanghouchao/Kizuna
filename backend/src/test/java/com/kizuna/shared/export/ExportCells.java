package com.kizuna.shared.export;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class ExportCells {
  private static final Pattern ASCII_WORD = Pattern.compile("[\\x20-\\x7e]*[A-Za-z][\\x20-\\x7e]*");
  private static final Pattern TIMESTAMP =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(Z|[+-]\\d{2}:\\d{2})");

  private ExportCells() {}

  /** 英字だけで書かれた CSV のセル値。生成日時の ISO 表記は除く。 */
  public static List<String> asciiWords(String csv) {
    return Arrays.stream(csv.replace("﻿", "").split("\r\n"))
        .flatMap(line -> Arrays.stream(line.split("\",\"")))
        .map(cell -> cell.replaceAll("^\"|\"$", "").replaceFirst("^'", ""))
        .filter(cell -> ASCII_WORD.matcher(cell).matches() && !TIMESTAMP.matcher(cell).matches())
        .distinct()
        .toList();
  }
}
