package com.kizuna.user.api.dto;

import com.kizuna.user.application.ServiceIdentityViews;
import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Set;

/**
 * サービスID一覧 1 件の要約応答。対象範囲（ロール×店舗集合）と状態の表示に必要な項目だけを持ち、 楽観ロック用 version は詳細（GET
 * /service-identities/{id}）が持つ — 授権編集は詳細を取り直してから始める。
 */
public record ServiceIdentitySummaryResponse(
    Long id,
    String displayName,
    boolean enabled,
    List<ServiceIdentityRoleRef> roles,
    StoreScopeType storeScopeType,
    Set<Long> storeIds) {

  public static ServiceIdentitySummaryResponse of(ServiceIdentityViews.Summary s) {
    return new ServiceIdentitySummaryResponse(
        s.id(),
        s.displayName(),
        s.enabled(),
        s.roles().stream().map(ServiceIdentityRoleRef::of).toList(),
        s.storeScopeType(),
        s.storeIds());
  }
}
