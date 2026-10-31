package com.kizuna.auth.api.dto;

/** トークンを返す端点の応答。リフレッシュトークン自体は本文に載せず、HttpOnly cookie で渡す。 */
public record Token(String token, long expiresAt, long userId, long refreshExpiresAt) {}
