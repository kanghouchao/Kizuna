package com.kizuna.remuneration.domain;

import com.kizuna.shared.exception.ServiceException;

public final class RemunerationAmounts {
  public static final long MAX = 9_007_199_254_740_991L;

  private RemunerationAmounts() {}

  public static long require(long value) {
    if (value < 0 || value > MAX) throw new ServiceException("金額が扱える範囲を超えています");
    return value;
  }

  public static long add(long a, long b) {
    return require(Math.addExact(require(a), require(b)));
  }
}
