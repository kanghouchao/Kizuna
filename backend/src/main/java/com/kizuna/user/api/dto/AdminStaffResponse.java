package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Set;

/**
 * 従業員（ロール×店舗集合）の応答。JSON キーは Jackson 設定により snake_case（display_name / store_scope_type / store_ids）。
 * 店舗名は解決せず id のみ返す（フロントは授権店舗一覧の id→name で解決する）。ロールは選択 UI と一覧表示のため id と名称を返す。 version
 * は楽観ロックの往復用（編集リクエストがそのまま返送し、不一致は 409）。
 *
 * <p>{@code editable} は委譲守衛の判定結果（対象の権限並集と店舗集合が行使者の部分集合か）で、行使者ごとに変わる。
 * 一覧に出るか（行使者の店舗集合と交わるか）とは別の軸で、false の行は見えるが編集・停止・再設定できない。
 */
public record AdminStaffResponse(
    Long id,
    String email,
    String displayName,
    boolean enabled,
    List<RoleRef> roles,
    StoreScopeType storeScopeType,
    Set<Long> storeIds,
    long version,
    boolean editable) {

  /** ロールへの参照（id と名称）。 */
  public record RoleRef(Long id, String name) {}
}
