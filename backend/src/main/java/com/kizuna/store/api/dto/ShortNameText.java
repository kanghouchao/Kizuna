package com.kizuna.store.api.dto;

import java.util.regex.Pattern;

/** 略称の前後の空白除去。フロントエンドの {@code trim()} と同じ集合（NBSP・BOM・全角空白を含む）を除く。 */
final class ShortNameText {

  private static final Pattern BOUNDARY_SPACES =
      Pattern.compile("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$");

  private ShortNameText() {}

  static String trim(String value) {
    return value == null ? null : BOUNDARY_SPACES.matcher(value).replaceAll("");
  }
}
