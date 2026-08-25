package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Set;

/**
 * 店舗スタッフ（ロール×店舗集合）の応答。JSON キーは Jackson 設定により snake_case。store_ids は id のみ返し、店舗名は解決しない（GET
 * /platform/stores の id→name テーブルで解決する）。
 *
 * <p>{@code editable} は防提権守衛 G3 の判定結果で、行使者ごとに変わる。前端に「店舗側ロールとは何か」「STAFF_MANAGE
 * 実効保持とは何か」を複製させないための単源であり、表示可否（一覧に出るか）とは別の軸である。
 */
public record StoreStaffResponse(
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
