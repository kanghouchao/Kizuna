package com.kizuna.user.api.dto;

import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.StoreScopeType;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * スタッフ権限編集リクエスト。ロール×店舗集合のみを更新する（email/displayName/password は含めない）。JSON キーは Jackson 設定により snake_case
 * （store_scope_type / store_ids）。
 */
@Getter
@Setter
@NoArgsConstructor
public class PlatformStaffUpdateRequest {

  @NotNull(message = "role is required")
  private PlatformRole role;

  @NotNull(message = "store_scope_type is required")
  private StoreScopeType storeScopeType;

  private Set<Long> storeIds;
}
