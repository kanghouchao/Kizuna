package com.kizuna.auth.infrastructure;

import lombok.Getter;

/** リフレッシュトークンの拒否。原因は応答ヘッダ {@code X-Auth-Failure} で利用者側へ区別して伝える。 */
@Getter
public class RefreshTokenRejectedException extends RuntimeException {

  public enum Reason {
    CREDENTIAL_CHANGED("credential_changed", "権限やパスワードが変更されました。もう一度ログインしてください"),
    MISSING("missing", "ログインしてください"),
    EXPIRED("expired", "ログインの有効期限が切れました。もう一度ログインしてください"),
    INVALID("invalid", "認証に失敗しました");

    private final String headerValue;
    private final String message;

    Reason(String headerValue, String message) {
      this.headerValue = headerValue;
      this.message = message;
    }

    public String headerValue() {
      return headerValue;
    }

    public String message() {
      return message;
    }
  }

  private final Reason reason;

  public RefreshTokenRejectedException(Reason reason) {
    super(reason.headerValue());
    this.reason = reason;
  }
}
