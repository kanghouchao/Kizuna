package com.kizuna.auth.infrastructure;

import com.kizuna.shared.web.JwtClaims;

/** auth の中だけで発行と検証が合意する JWT の claim 名。auth の外も読む利用者 ID は {@link JwtClaims} にある。 */
public final class TokenClaims {

  /** トークンの種別（{@link TokenType}）。 */
  public static final String TYPE = "typ";

  /** ログインごとに採番するセッション ID。リフレッシュで発行し直すトークンも同じ値を引き継ぎ、ログアウトはこの単位で失効させる。 */
  public static final String SESSION_ID = "sid";

  /** 発行時点の資格情報の版。検証時に現在の版と相等比較する。 */
  public static final String CREDENTIAL_VERSION = "credentialVersion";

  /** 付与された権限（{@code PERM_*}・{@code ROLE_*}）。 */
  public static final String AUTHORITIES = "authorities";

  private TokenClaims() {}
}
