package com.kizuna.auth.api.dto;

import java.time.OffsetDateTime;

/**
 * 緊急昇格の履歴一覧 1 行分。昇格トークンの生値はこの型に存在しない（発動応答にしか現れない）。
 *
 * <p>{@code status} は読み時点の実効状態 {@code ACTIVE} / {@code EXPIRED} / {@code REVOKED}。記録の状態列は
 * 期限切れを持たない（ADR 0024）ため、期限の比較はサーバの読み口が行い、呼出側の時計に委ねない。
 */
public record EmergencyElevationSummaryResponse(
    Long id,
    String activatedByName,
    Long targetStoreId,
    String storeName,
    String reason,
    OffsetDateTime activatedAt,
    OffsetDateTime expiresAt,
    String status,
    String revokedByName,
    OffsetDateTime revokedAt) {}
