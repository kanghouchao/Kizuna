package com.kizuna.order.domain;

/** 受注の受付経路。広告費・効果集計の根拠となるため、推測ではなく実際の受付手段を記録する。 */
public enum ReceptionRoute {
  /** 会員ポータルからの Web 申請。 */
  WEB,
  /** 店舗が電話で受け付けた予約。 */
  PHONE
}
