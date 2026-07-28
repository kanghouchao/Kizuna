package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Set;

/**
 * スタッフ（ロール×店舗集合）の応答。JSON キーは Jackson 設定により snake_case（display_name / store_scope_type /
 * store_ids）。店舗名は解決せず id のみ返す（フロントは GET /platform/stores の id→name テーブルで解決する）。ロールは選択 UI と一覧表示のため id
 * と名称を返す。version は楽観ロックの往復用 （編集リクエストがそのまま返送し、不一致は 409）。
 */
public record PlatformStaffResponse(
    Long id,
    String email,
    String displayName,
    boolean enabled,
    List<RoleRef> roles,
    StoreScopeType storeScopeType,
    Set<Long> storeIds,
    long version) {

  /** ロールへの参照（id と名称）。 */
  public record RoleRef(Long id, String name) {}
}
