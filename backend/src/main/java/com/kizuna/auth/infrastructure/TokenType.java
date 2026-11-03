package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;

/**
 * 発行するトークンの種別。署名鍵を共有するため、検証側は種別 claim を照合して互いの代用を拒否する（リフレッシュトークンを Bearer に、アクセストークンを リフレッシュに使わせない）。
 */
public enum TokenType {
  ACCESS("access"),
  REFRESH("refresh");

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
