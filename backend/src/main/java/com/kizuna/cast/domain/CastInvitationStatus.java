package com.kizuna.cast.domain;

/** キャスト一覧に表示する招待状態。招待の物理状態と有効期限、在籍状態、本人の紐づけ有無から導出する。 */
public enum CastInvitationStatus {
  /** 招待が一度も発行されていない。 */
  NOT_INVITED,
  /** 未期限の PENDING 招待が存在する。 */
  INVITED,
  /** PENDING 招待が存在するが期限切れ。 */
  EXPIRED,
  /** 档案に平台身分が紐づいている。 */
  LINKED,
  /** 未紐づけのまま退店し、招待を利用できない。 */
  UNAVAILABLE
}
