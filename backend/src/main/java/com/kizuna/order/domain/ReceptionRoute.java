package com.kizuna.order.domain;

/** 受注の受付経路。広告費・効果集計の根拠となるため、推測ではなく実際の受付手段を記録する。 */
public enum ReceptionRoute {
  /** 会員ポータルからの Web 申請。 */
  MEMBER_WEB,
  /** 公開店面からのゲスト Web 申請。 */
  GUEST_WEB,
  /** 店舗が電話で受け付けた予約。 */
  PHONE;

  /** 店舗・HQ が受注の作成で名乗ってよい経路か。許す値を数え上げる形にしてあるのは、値が増えたときに 既定で拒否側へ倒すため — 拒否する値を数え上げると、新しい値が守衛を素通りする。 */
  public boolean isStoreSelectable() {
    return this == PHONE;
  }
}
