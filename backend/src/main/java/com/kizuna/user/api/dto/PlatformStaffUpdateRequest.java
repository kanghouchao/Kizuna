package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * スタッフ授権編集リクエスト。能力束×店舗集合×精算範囲のみを更新する（email/displayName/password は含めない）。JSON キーは Jackson 設定により
 * snake_case（bundle_ids / store_scope_type / store_ids / settlement_scope_type /
 * settlement_store_ids）。
 */
@Getter
@Setter
@NoArgsConstructor
public class PlatformStaffUpdateRequest {

  @NotEmpty(message = "bundle_ids is required")
  private Set<Long> bundleIds;

  @NotNull(message = "store_scope_type is required")
  private StoreScopeType storeScopeType;

  private Set<Long> storeIds;

  private StoreScopeType settlementScopeType;

  private Set<Long> settlementStoreIds;
}
