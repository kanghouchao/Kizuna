package com.kizuna.user.api;

import com.kizuna.user.api.dto.PermissionGroupResponse;
import com.kizuna.user.api.dto.PermissionResponse;
import com.kizuna.user.api.dto.PermissionTreeResponse;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionGroup;
import com.kizuna.user.domain.PermissionTree;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 権限目録 API（ロール編集 UI の選択肢データ源）。ROLE_MANAGE 権限限定。
 *
 * <p>目録はコード定義（{@link PermissionCode}）が正本であり、DB の t_permissions 行はその播種済み写像であるため enum から直接組み立てる。 「木 →
 * 区分見出し → 権限」の入れ子で、各段は {@link PermissionTree}・{@link PermissionGroup}・{@link PermissionCode}
 * の宣言順。権限の無い見出しは出さない。
 */
@RestController
@RequestMapping("/permissions")
public class PermissionController {

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<List<PermissionTreeResponse>> list() {
    return ResponseEntity.ok(
        Arrays.stream(PermissionTree.values())
            .map(tree -> new PermissionTreeResponse(tree.name(), tree.getLabel(), groupsOf(tree)))
            .toList());
  }

  private static List<PermissionGroupResponse> groupsOf(PermissionTree tree) {
    return Arrays.stream(PermissionGroup.values())
        .map(
            group ->
                new PermissionGroupResponse(
                    group.name(),
                    group.getLabel(),
                    Arrays.stream(PermissionCode.values())
                        .filter(code -> code.getTree() == tree && code.getGroup() == group)
                        .map(code -> new PermissionResponse(code.name(), code.getLabel()))
                        .toList()))
        .filter(group -> !group.permissions().isEmpty())
        .toList();
  }
}
