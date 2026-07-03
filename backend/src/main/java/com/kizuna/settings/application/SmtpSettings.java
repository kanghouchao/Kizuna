package com.kizuna.settings.application;

/**
 * SMTP 設定の型付きスナップショット。キー名（smtp_host 等）の知識は settings モジュールだけが持ち、 消費側（notification）はこの型のみに依存する。
 *
 * @param host SMTP ホスト（未設定なら空文字）
 * @param port SMTP ポート（未設定なら 25）
 * @param username 認証ユーザー名（未設定なら空文字）
 * @param password 認証パスワード（未設定なら空文字）
 * @param from 送信元アドレス（未設定なら空文字）
 */
public record SmtpSettings(String host, int port, String username, String password, String from) {

  /** DB に SMTP ホストが設定されているか（false ならフォールバック送信経路を使う）。 */
  public boolean configured() {
    return host != null && !host.isBlank();
  }

  public boolean hasAuth() {
    return username != null && !username.isBlank();
  }

  public boolean hasFrom() {
    return from != null && !from.isBlank();
  }
}
