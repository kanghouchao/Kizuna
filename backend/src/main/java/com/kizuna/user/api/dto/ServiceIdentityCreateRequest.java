package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * サービスID新規作成リクエスト。資格情報（email/password）は持たない — サービスIDは対話ログインできない（ADR 0025）。 JSON キーは Jackson 設定により
 * snake_case（display_name / role_ids / store_scope_type / store_ids）。
 */
@Getter
@Setter
@NoArgsConstructor
public class ServiceIdentityCreateRequest {

  @NotBlank(message = "display_name is required")
  // t_users.display_name VARCHAR(150)。列長超過は制約名を持たない整合性違反として写像に載らず 500 に落ちるため、
  // 上限は要求の側で止める。
  @Size(max = 150)
  private String displayName;

  @NotEmpty(message = "role_ids is required")
  private Set<Long> roleIds;

  @NotNull(message = "store_scope_type is required")
  private StoreScopeType storeScopeType;

  private Set<Long> storeIds;
}
