package com.kizuna.user.api.dto;

import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.StoreScopeType;
import java.util.Set;

/**
 * スタッフ（ロール×店舗集合）の応答。JSON キーは Jackson 設定により snake_case（display_name / store_scope_type /
 * store_ids）。店舗名は解決せず id のみ返す（フロントは GET /platform/stores の id→name テーブルで解決する）。
 */
public record PlatformStaffResponse(
    Long id,
    String email,
    String displayName,
    PlatformRole role,
    StoreScopeType storeScopeType,
    Set<Long> storeIds) {}
