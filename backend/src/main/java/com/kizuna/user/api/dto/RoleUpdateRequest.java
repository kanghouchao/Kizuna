package com.kizuna.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ロール編集リクエスト。permissions は権限コード（PermissionCode enum 名）で指定する。 */
@Getter
@Setter
@NoArgsConstructor
public class RoleUpdateRequest {

  @NotBlank(message = "name is required")
  private String name;

  @NotEmpty(message = "permissions is required")
  private Set<String> permissions;

  /** 楽観ロック用バージョン（応答の version をそのまま往復する。不一致は 409）。 */
  @NotNull(message = "version is required")
  private Long version;
}
