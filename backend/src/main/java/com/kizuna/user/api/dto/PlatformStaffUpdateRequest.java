package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * スタッフ授権編集リクエスト。ロール×店舗集合のみを更新する（email/displayName/password は含めない）。JSON キーは Jackson 設定により
 * snake_case（role_ids / store_scope_type / store_ids）。
 */
@Getter
@Setter
@NoArgsConstructor
public class PlatformStaffUpdateRequest {

  @NotEmpty(message = "role_ids is required")
  private Set<@NotNull(message = "role_ids must not contain null") Long> roleIds;

  @NotNull(message = "store_scope_type is required")
  private StoreScopeType storeScopeType;

  // null 要素は永続化時に黙って捨てられ、非空検証を通ったのに店舗ゼロの行が残るため要素単位で拒む。
  private Set<@NotNull(message = "store_ids must not contain null") Long> storeIds;

  /** 楽観ロック用バージョン（応答の version をそのまま往復する。不一致は 409）。 */
  @NotNull(message = "version is required")
  private Long version;
}
