package com.kizuna.customer.api.dto;

/**
 * 統合履歴 1 件が、問い合わせた顧客から見てどちら側だったか。
 *
 * <p>統合そのものの属性ではなく読み手との関係なので、記録（{@code CustomerMerge}）ではなく応答の側に置く。同じ 1 件が、存続行の画面では {@link
 * #SURVIVING}、被統合行の画面では {@link #MERGED} として現れる。
 */
public enum MergeDirection {
  /** この行が存続行として統合を受けた。 */
  SURVIVING,
  /** この行が被統合となり、墓標になった。 */
  MERGED
}
