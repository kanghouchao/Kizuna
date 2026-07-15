package com.kizuna.cast.domain;

/** キャスト一覧に表示する招待状態（サービス層で導出する四態）。物理状態（{@link CastInvitation.Status}）と有効期限・档案の紐づけ有無から導出する。 */
public enum CastInvitationStatus {
  /** 招待が一度も発行されていない。 */
  NOT_INVITED,
  /** 未期限の PENDING 招待が存在する。 */
  INVITED,
  /** PENDING 招待が存在するが期限切れ。 */
  EXPIRED,
  /** 档案に平台身分が紐づいている。 */
  LINKED
}
