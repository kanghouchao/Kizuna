package com.kizuna.user.api.dto;

import com.kizuna.shared.validation.Password;
import com.kizuna.user.domain.StoreScopeType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
  // 永続化前の小文字化で最大2倍に伸長する文字(U+0130 等)があっても t_users.email VARCHAR(255) に収まる上限。
  @Size(max = 127)
  private String email;

  @NotBlank(message = "password is required")
  @Password
  private String password;

  @NotBlank(message = "display_name is required")
  // t_users.display_name VARCHAR(150)。列長超過は制約名を持たない整合性違反として写像に載らず 500 に落ちるため、
  // 上限は要求の側で止める。
  @Size(max = 150)
  private String displayName;

  @NotEmpty(message = "role_ids is required")
  private Set<@NotNull(message = "role_ids must not contain null") Long> roleIds;

  @NotNull(message = "store_scope_type is required")
  private StoreScopeType storeScopeType;

  // null 要素は永続化時に黙って捨てられ、非空検証を通ったのに店舗ゼロの行が残るため要素単位で拒む。
  private Set<@NotNull(message = "store_ids must not contain null") Long> storeIds;
}
