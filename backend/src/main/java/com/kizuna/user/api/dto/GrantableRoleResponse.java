package com.kizuna.user.api.dto;

import com.kizuna.user.application.RoleViews;
import java.util.List;

/**
 * 従業員に付与できるロール 1 件の応答。授与の前に「この設定の結果」で何が渡るかを読めるよう、権限を目録の宣言順で持つ。
 * 見えるのは行使者が付与できるロールだけで、その権限は行使者の権限並集に収まる。
 */
public record GrantableRoleResponse(
    Long id, String name, boolean system, List<PermissionResponse> permissions) {

  public static GrantableRoleResponse of(RoleViews.Grantable g) {
    return new GrantableRoleResponse(
        g.id(),
        g.name(),
        g.system(),
        g.permissions().stream()
            .map(code -> new PermissionResponse(code.name(), code.getLabel()))
            .toList());
  }
}
