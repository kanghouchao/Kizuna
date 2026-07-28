package com.kizuna.user.api.dto;

import com.kizuna.user.domain.StoreScopeType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * スタッフ新規作成リクエスト。JSON キーは Jackson 設定により snake_case（display_name / role_ids / store_scope_type /
 * store_ids）。
 */
@Getter
@Setter
@NoArgsConstructor
public class PlatformStaffCreateRequest {

  @NotBlank(message = "email is required")
  @Email(message = "email format is invalid")
  private String email;

  @NotBlank(message = "password is required")
  private String password;

  @NotBlank(message = "display_name is required")
  private String displayName;

  @NotEmpty(message = "role_ids is required")
  private Set<Long> roleIds;

  @NotNull(message = "store_scope_type is required")
  private StoreScopeType storeScopeType;

  private Set<Long> storeIds;
}
