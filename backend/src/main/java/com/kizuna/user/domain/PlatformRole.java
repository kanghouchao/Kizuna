package com.kizuna.user.domain;

/** プラットフォームユーザーのロール。授権は「ロール×店舗集合」で表す（1 ユーザー = 1 ロール）。 エリアマネージャ等の追加値は後続チケットで拡張する（#320/#321）。 */
public enum PlatformRole {
  HQ_ADMIN,
  STORE_MANAGER,
  STORE_STAFF,
  CAST,
  MEMBER
}
