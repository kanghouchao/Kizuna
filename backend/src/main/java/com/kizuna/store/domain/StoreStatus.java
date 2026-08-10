package com.kizuna.store.domain;

/**
 * 店舗の稼働状態。
 *
 * <p>遷移は PREPARING → ACTIVE の一方向のみで、稼働中から準備中へは戻せない。開店してしまった店舗を 準備中へ戻せると、確定した記録を抱えたまま削除できる状態へ後退するため。
 */
public enum StoreStatus {

  /** 準備中。開店前の下ごしらえ期間で、この間だけ店舗を削除できる。 */
  PREPARING,

  /** 稼働中。店舗側の利用者が店舗コンソールへ入った時点で到達する。 */
  ACTIVE
}
