package com.kizuna.user.api;

import com.kizuna.user.api.dto.PermissionGroupResponse;
import com.kizuna.user.api.dto.PermissionResponse;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionGroup;
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
 * <p>目録はコード定義（{@link PermissionCode}）が正本であり、DB の t_permissions 行はその播種済み写像であるため enum から直接組み立てる。 組は
 * {@link PermissionGroup} の宣言順、組内は宣言順で返す。
 */
@RestController
@RequestMapping("/permissions")
public class PermissionController {

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<List<PermissionGroupResponse>> list() {
    return ResponseEntity.ok(
        Arrays.stream(PermissionGroup.values())
            .map(
                group ->
                    new PermissionGroupResponse(
                        group.name(),
                        group.getLabel(),
                        Arrays.stream(PermissionCode.values())
                            .filter(code -> code.getGroup() == group)
                            .map(code -> new PermissionResponse(code.name(), code.getLabel()))
                            .toList()))
            .toList());
  }
}
