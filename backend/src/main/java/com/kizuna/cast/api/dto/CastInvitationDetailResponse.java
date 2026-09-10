package com.kizuna.cast.api.dto;

import java.time.OffsetDateTime;

/**
 * 招待照会の応答。status は VALID（受諾可能）、EXPIRED（期限切れ）、USED（受諾済み・失効）、 UNAVAILABLE（退店により受諾不可）。castName
 * は新規登録の表示名初期値に使う。
 */
public record CastInvitationDetailResponse(
    String storeName, String castName, String status, OffsetDateTime expiresAt) {}
