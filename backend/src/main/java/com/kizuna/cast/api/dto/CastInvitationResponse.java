package com.kizuna.cast.api.dto;

import java.time.OffsetDateTime;

/**
 * 招待発行の応答。JSON キーは Jackson 設定により snake_case（expires_at）。 招待リンク URL はバックエンドが持たず、フロントが {@code origin
 * + /platform/invite/{token}} で組む。
 */
public record CastInvitationResponse(String token, OffsetDateTime expiresAt) {}
