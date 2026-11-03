package com.kizuna.auth.application;

import com.kizuna.auth.infrastructure.IssuedJwt;

/** 同じセッション（sid）で発行したアクセストークンとリフレッシュトークンの組。 */
public record IssuedSession(long userId, IssuedJwt access, IssuedJwt refresh) {}
