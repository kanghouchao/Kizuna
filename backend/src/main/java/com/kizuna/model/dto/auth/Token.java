package com.kizuna.model.dto.auth;

public record Token(String token, long expiresAt) {}
