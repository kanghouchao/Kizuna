package com.kizuna.auth.api.dto;

import java.util.List;

/**
 * GET /platform/me の応答。JSON キーは Jackson 設定により snake_case（display_name / store_scope_type /
 * store_ids）。
 */
public record PlatformMeResponse(
    String email, String displayName, String role, String storeScopeType, List<Long> storeIds) {}
