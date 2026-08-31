package com.kizuna.auth.api.dto;

/**
 * 緊急昇格の発動結果。トークンが現れるのはこの応答だけで、後から取り直す口は無い（一覧・詳細の DTO には載せない）。
 *
 * <p>{@code expiresAt} は epoch ミリ秒で、{@link Token} と同じ表現に揃える。値は発動記録の期限そのものであり、 トークンの exp
 * と同一の時刻を指す（秒精度への切り捨ては JWT 仕様側の話）。
 */
public record EmergencyElevationActivationResponse(Long id, String token, long expiresAt) {}
