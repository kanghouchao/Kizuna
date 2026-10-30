package com.kizuna.shared.export;

import java.util.Arrays;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public final class ExportCells {
  private static final Pattern LETTERS = Pattern.compile("[A-Za-z]+");
  private static final Pattern TIMESTAMP =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(Z|[+-]\\d{2}:\\d{2})");

  private ExportCells() {}

  /** CSV の全セルに現れる英字の並び。日本語に混ざった英字も拾い、生成日時の ISO 表記は除く。 */
  public static List<String> letterRuns(String csv) {
    return Arrays.stream(csv.replace("﻿", "").split("\r\n"))
        .flatMap(line -> Arrays.stream(line.split("\",\"")))
        .map(cell -> cell.replaceAll("^\"|\"$", "").replaceFirst("^'", ""))
        .filter(cell -> !TIMESTAMP.matcher(cell).matches())
        .flatMap(cell -> LETTERS.matcher(cell).results().map(MatchResult::group))
        .distinct()
        .toList();
  }
}
