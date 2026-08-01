package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.RoleCreateRequest;
import com.kizuna.user.api.dto.RoleResponse;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.RoleUpdateRequest;
import com.kizuna.user.application.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ロール管理 API（RBAC）。全操作 STAFF_MANAGE 権限限定。 */
@RestController
@RequestMapping("/platform/roles")
@RequiredArgsConstructor
public class RoleController {

  private final RoleService roleService;

  /** 一覧は権限個数までの要約。編集フォームが要る権限コードの列挙は {@link #get(Long)} で個別に取得する。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<List<RoleSummaryResponse>> list() {
    return ResponseEntity.ok(roleService.list());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<RoleResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(roleService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleCreateRequest req) {
    return ResponseEntity.ok(roleService.create(req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<RoleResponse> update(
      @PathVariable Long id, @Valid @RequestBody RoleUpdateRequest req) {
    return ResponseEntity.ok(roleService.update(id, req));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    roleService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
