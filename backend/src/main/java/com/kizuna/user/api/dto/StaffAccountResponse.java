package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Set;

/**
 * アカウント面の詳細。要約に現在の授権（表示専用）を足す。JSON キーは Jackson 設定により snake_case（display_name / store_scope_type /
 * store_ids）。店舗名は解決せず id のみ返す。
 *
 * <p>要約と同じく version も授権を書ける項目も持たない — この面が何も付与しないことは実行時の分岐ではなく型が担保する。
 */
public record StaffAccountResponse(
    Long id,
    String email,
    String displayName,
    boolean enabled,
    List<StaffAccountRoleRef> roles,
    StoreScopeType storeScopeType,
    Set<Long> storeIds) {}
