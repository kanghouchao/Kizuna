package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Set;

/**
 * サービスIDの詳細応答（GET /{id} と作成・更新の応答）。JSON キーは Jackson 設定により snake_case（display_name /
 * store_scope_type / store_ids）。店舗名は解決せず id のみ返す（フロントは授権店舗一覧の id→name テーブルで解決する）。version
 * は楽観ロックの往復用（編集リクエストがそのまま返送し、不一致は 409）。
 */
public record ServiceIdentityResponse(
    Long id,
    String displayName,
    boolean enabled,
    List<ServiceIdentityRoleRef> roles,
    StoreScopeType storeScopeType,
    Set<Long> storeIds,
    long version) {}
