package com.kizuna.auth.api.dto;

/**
 * POST /auth/line/login の応答。JSON キーは Jackson 設定により snake_case（expires_at / registration_ticket /
 * display_name）で、null 項目は応答から省かれる。
 *
 * <p>連携済み（{@code registered=true}）なら token・expires_at・user_id・refresh_expires_at を、未登録なら登録チケットと
 * LINE 表示名を返す 2 形態を取る。 未登録側で token を返さないことが、なりすまし（LINE 側のメール一致による既存アカウントへの自動ログイン）を構造的に排除している。
 */
public record LineLoginResponse(
    boolean registered,
    String token,
    Long expiresAt,
    Long userId,
    Long refreshExpiresAt,
    String registrationTicket,
    String displayName) {

  /** 連携済み LINE アカウントのログイン成功。 */
  public static LineLoginResponse registered(Token issued) {
    return new LineLoginResponse(
        true,
        issued.token(),
        issued.expiresAt(),
        issued.userId(),
        issued.refreshExpiresAt(),
        null,
        null);
  }

  /** 未登録 LINE アカウント。登録確定要求で使う 1 度きりのチケットと、初期表示名を返す。 */
  public static LineLoginResponse unregistered(String registrationTicket, String displayName) {
    return new LineLoginResponse(false, null, null, null, null, registrationTicket, displayName);
  }
}
