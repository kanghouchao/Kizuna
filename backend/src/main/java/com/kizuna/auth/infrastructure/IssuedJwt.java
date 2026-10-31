package com.kizuna.auth.infrastructure;

import java.time.Instant;

/** 署名済みのトークン文字列とその期限。 */
public record IssuedJwt(String value, Instant expiresAt) {}
