package com.kizuna.shared.exception;

/**
 * 認証セッション（AuthSession）が指す主体が既に存在しないことを表す例外。HTTP 401 で応答される。
 *
 * <p>トークン自体は有効期限内で署名も正しいが、それが指す利用者の行が引けない状態を指す。要求の誤りではないので 400 ではなく、要求された資源（{@code /platform/me}
 * 等）は概念上存在するので 404 でもない — 無効なのはセッションであり、正しい回復手段は再ログインである。
 *
 * <p>現状この状態はアプリケーションの操作からは到達しない（利用者行の削除経路が無く、停止は {@code enabled=false} で行を残す）。
 * 到達した場合は不変量が壊れているため、応答は 401 でも記録は {@code log.error} とし、通常のセッション期限切れに紛れて調査されないことを防ぐ。
 */
public class StaleSessionException extends RuntimeException {

  public StaleSessionException(String message) {
    super(message);
  }
}
