package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * サービスID授権編集リクエスト。ロール×店舗集合のみを更新する（display_name は含めない）。JSON キーは Jackson 設定により snake_case（role_ids /
 * store_scope_type / store_ids）。
 */
@Getter
@Setter
@NoArgsConstructor
public class ServiceIdentityUpdateRequest {

  @NotEmpty(message = "role_ids is required")
  private Set<Long> roleIds;

  @NotNull(message = "store_scope_type is required")
  private StoreScopeType storeScopeType;

  private Set<Long> storeIds;

  /** 楽観ロック用バージョン（応答の version をそのまま往復する。不一致は 409）。 */
  @NotNull(message = "version is required")
  private Long version;
}
