package com.kizuna.store;

import java.util.concurrent.atomic.AtomicInteger;

/** 実 DB へ店舗を挿入するテスト用に、略称（3 文字・一意制約付き）を重複なく払い出す。 */
public final class StoreShortNames {

  private static final int SPACE = 36 * 36 * 36;
  private static final AtomicInteger NEXT = new AtomicInteger((int) (Math.random() * SPACE));

  private StoreShortNames() {}

  public static String next() {
    String digits = Integer.toString(Math.floorMod(NEXT.getAndIncrement(), SPACE), 36);
    return "0".repeat(3 - digits.length()) + digits;
  }
}
