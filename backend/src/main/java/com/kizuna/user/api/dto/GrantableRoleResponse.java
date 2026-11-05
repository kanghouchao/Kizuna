package com.kizuna.user.api.dto;

import com.kizuna.user.application.RoleViews;
import java.util.List;

/**
 * 従業員に付与できるロール 1 件の応答。権限を目録の宣言順で持ち、授与の前に何が渡るかを画面で読ませる。
 * 行使者の権限並集に収まるロールだけが返るので、見える権限は行使者が既に持つものに限る。ROLE_MANAGE 保持者には全ロールが返るが、その中身は GET /roles/{id}
 * で既に読める。
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
