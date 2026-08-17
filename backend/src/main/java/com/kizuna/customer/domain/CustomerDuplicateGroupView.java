package com.kizuna.customer.domain;

/**
 * 重複候補のグループの見出し。件数は {@code having} が既に数えているので、行を引く前に判る。
 *
 * <p>行を引く前に件数が判ることに用がある — 桁外れに大きいグループ（移行データの代替値のような、識別の手がかりを持たない番号）を 行ごと読み込まずに見分けられる。
 */
public interface CustomerDuplicateGroupView {

  String getPhoneNumber();

  /** その番号を持つ生きた行の総数。返す行を上限で切っても、この数は切らない。 */
  long getTotal();
}
