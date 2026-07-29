package com.kizuna.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ロール新規作成リクエスト。permissions は権限コード（PermissionCode enum 名）で指定する。 */
@Getter
@Setter
@NoArgsConstructor
public class RoleCreateRequest {

  @NotBlank(message = "name is required")
  @Size(max = 100, message = "name must be at most 100 characters")
  private String name;

  @NotEmpty(message = "permissions is required")
  private Set<String> permissions;
}
