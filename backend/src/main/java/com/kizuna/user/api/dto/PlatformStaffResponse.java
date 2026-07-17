package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Set;

/**
 * スタッフ（能力束×店舗集合×精算範囲）の応答。JSON キーは Jackson 設定により snake_case（display_name / store_scope_type /
 * store_ids / settlement_scope_type / settlement_store_ids）。店舗名は解決せず id のみ返す（フロントは GET
 * /platform/stores の id→name テーブルで解決する）。束は選択 UI と一覧表示のため id と名称を返す。
 */
public record PlatformStaffResponse(
    Long id,
    String email,
    String displayName,
    boolean enabled,
    List<BundleRef> bundles,
    StoreScopeType storeScopeType,
    Set<Long> storeIds,
    StoreScopeType settlementScopeType,
    Set<Long> settlementStoreIds) {

  /** 能力束への参照（id と名称）。 */
  public record BundleRef(Long id, String name) {}
}
