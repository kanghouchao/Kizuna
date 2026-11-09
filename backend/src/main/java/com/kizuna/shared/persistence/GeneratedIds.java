package com.kizuna.shared.persistence;

import java.util.regex.Pattern;

/** 採番された文字列 ID（UUID の小文字 36 文字表記）の形式。外から渡された ID を照会の前に弾く。 */
public final class GeneratedIds {
  private static final Pattern FORMAT =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

  private GeneratedIds() {}

  public static boolean matches(String value) {
    return value != null && FORMAT.matcher(value).matches();
  }
}
