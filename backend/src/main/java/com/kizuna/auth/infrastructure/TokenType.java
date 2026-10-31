package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;

/**
 * 発行するトークンの種別。署名鍵を共有するため、検証側は種別 claim を照合して互いの代用を拒否する（リフレッシュトークンを Bearer に、アクセストークンを リフレッシュに使わせない）。
 */
public enum TokenType {
  ACCESS("access"),
  REFRESH("refresh");

  /** 種別 claim 名。 */
  public static final String CLAIM = "typ";

  /** ログインごとに採番するセッション ID の claim 名。リフレッシュで発行し直すトークンも同じ値を引き継ぎ、ログアウトはこの単位で失効させる。 */
  public static final String SESSION_ID_CLAIM = "sid";

  private final String claimValue;

  TokenType(String claimValue) {
    this.claimValue = claimValue;
  }

  public String claimValue() {
    return claimValue;
  }

  long lifetimeMillis(AppProperties appProperties) {
    AppProperties.Jwt jwt = appProperties.getJwt();
    return this == ACCESS ? jwt.getAccessExpiration() : jwt.getRefreshExpiration();
  }
}
