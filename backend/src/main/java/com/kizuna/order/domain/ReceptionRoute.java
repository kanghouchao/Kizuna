package com.kizuna.order.domain;

/** 受注の受付経路。広告費・効果集計の根拠となるため、推測ではなく実際の受付手段を記録する。 */
public enum ReceptionRoute {
  /** 会員ポータルからの Web 申請。 */
  MEMBER_WEB,
  /** 公開店面からのゲスト Web 申請。 */
  GUEST_WEB,
  /** 店舗が電話で受け付けた予約。 */
  PHONE;

  /** Web 申請の確定だけが名乗る値か。店舗・HQ の作成経路はこの群を拒否する。 */
  public boolean isWebApplication() {
    return this == MEMBER_WEB || this == GUEST_WEB;
  }
}
