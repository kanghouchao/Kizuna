package com.kizuna.user.api.dto;

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

/** 店舗スタッフ新規作成リクエスト。JSON キーは Jackson 設定により snake_case。 */
@Getter
@Setter
@NoArgsConstructor
public class StoreStaffCreateRequest {

  @NotBlank(message = "email is required")
  @Email(message = "email format is invalid")
  // 永続化前の小文字化で最大2倍に伸長する文字(U+0130 等)があっても t_users.email VARCHAR(255) に収まる上限。
  @Size(max = 127)
  private String email;

  @NotBlank(message = "password is required")
  private String password;

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
