package com.kizuna.notificationdelivery.domain;

/** 結果不明の通知に職員が下せる判定。提供元の記録で確かめた送達済み・未送達の二つだけ。 */
public enum DeliveryResolution {
  SENT,
  FAILED;

  public DeliveryStatus status() {
    return this == SENT ? DeliveryStatus.SENT : DeliveryStatus.FAILED;
  }
}
