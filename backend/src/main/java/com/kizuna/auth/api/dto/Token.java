package com.kizuna.auth.api.dto;

import com.kizuna.auth.application.IssuedSession;

/** トークンを返す端点の応答。リフレッシュトークン自体は本文に載せず、HttpOnly cookie で渡す。 */
public record Token(String token, long expiresAt, long userId, long refreshExpiresAt) {

  public static Token of(IssuedSession session) {
    return new Token(
        session.access().value(),
        session.access().expiresAt().toEpochMilli(),
        session.userId(),
        session.refresh().expiresAt().toEpochMilli());
  }
}
