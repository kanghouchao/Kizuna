package com.kizuna.store.dashboard;

/** 店舗集合の店舗数と、稼働状態ごとの内訳。 */
public record StoreStatusCounts(long total, long preparing, long active) {
  public static final StoreStatusCounts NONE = new StoreStatusCounts(0, 0, 0);
}
