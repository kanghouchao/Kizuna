package com.kizuna.user.api.dto;

import com.kizuna.user.domain.GrantAction;
import java.time.OffsetDateTime;

/**
 * 付与履歴 1 件の応答。JSON キーは Jackson 設定により snake_case（actor_email / created_at）。detail は授権内容の快照（JSON
 * 文字列）。
 */
public record GrantHistoryEntryResponse(
    Long id, String actorEmail, GrantAction action, String detail, OffsetDateTime createdAt) {}
