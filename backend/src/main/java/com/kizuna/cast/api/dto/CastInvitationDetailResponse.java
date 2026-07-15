package com.kizuna.cast.api.dto;

import java.time.OffsetDateTime;

/**
 * 招待照会（公開ランディング）の応答。JSON キーは Jackson 設定により snake_case（store_name / cast_name / expires_at）。
 *
 * <p>{@code status} は受諾可否のビュー状態: {@code VALID}（受諾可能）/ {@code EXPIRED}（期限切れ）/ {@code
 * USED}（受諾済みまたは失効）。 {@code castName} は新規登録の表示名初期値に使う（裁定 7）。
 */
public record CastInvitationDetailResponse(
    String storeName, String castName, String status, OffsetDateTime expiresAt) {}
