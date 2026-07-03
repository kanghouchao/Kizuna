package com.kizuna.auth.api.dto;

public record Token(String token, long expiresAt) {}
