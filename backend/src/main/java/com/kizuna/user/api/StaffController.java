package com.kizuna.user.api;

import com.kizuna.user.api.dto.AdminStaffCreateRequest;
import com.kizuna.user.api.dto.AdminStaffResponse;
import com.kizuna.user.api.dto.AdminStaffUpdateRequest;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.StaffAccountPasswordResetResponse;
import com.kizuna.user.application.AdminStaffService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 従業員管理 API。一覧・作成・授権変更は STAFF_MANAGE、停止・再開・パスワード代理再設定は STAFF_CREDENTIAL_MANAGE 限定。
 * どの行使者も自分の店舗集合に収まる従業員だけを見て、自分の権限と店舗集合の部分集合だけを配れる。
 */
@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {

  private final AdminStaffService adminStaffService;

  /** 一覧は行使者の店舗集合と交わる者に絞る。並びは全順序（表示名 + id）— 副キーが無いと更新のたびに行が頁を跨いで滑る。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<Page<AdminStaffResponse>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Long storeId,
      @PageableDefault(sort = {"displayName", "id"}) Pageable pageable,
      Principal principal) {
    return ResponseEntity.ok(
        adminStaffService.list(search, storeId, pageable, principal.getName()));
  }

  /** 行使者が付与できるロールの目録。有界（ロール目録）なので裸の List で返す。 */
  @GetMapping("/grantable-roles")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<List<RoleSummaryResponse>> grantableRoles(Principal principal) {
    return ResponseEntity.ok(adminStaffService.grantableRoles(principal.getName()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<AdminStaffResponse> get(@PathVariable Long id, Principal principal) {
    return ResponseEntity.ok(adminStaffService.get(id, principal.getName()));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<AdminStaffResponse> create(
      @Valid @RequestBody AdminStaffCreateRequest req, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminStaffService.create(req, principal.getName()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<AdminStaffResponse> update(
      @PathVariable Long id, @Valid @RequestBody AdminStaffUpdateRequest req, Principal principal) {
    return ResponseEntity.ok(adminStaffService.update(id, req, principal.getName()));
  }

  @PostMapping("/{id}/suspension")
  @PreAuthorize("hasAuthority('PERM_STAFF_CREDENTIAL_MANAGE')")
  public ResponseEntity<Void> suspend(@PathVariable Long id, Principal principal) {
    adminStaffService.suspend(id, principal.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/resumption")
  @PreAuthorize("hasAuthority('PERM_STAFF_CREDENTIAL_MANAGE')")
  public ResponseEntity<Void> resume(@PathVariable Long id, Principal principal) {
    adminStaffService.resume(id, principal.getName());
    return ResponseEntity.noContent().build();
  }

  /** 仮パスワードを発行して再設定する。生値を返すのはこの応答だけで、資源を作らないので 200 を返す。 */
  @PostMapping("/{id}/password-reset")
  @PreAuthorize("hasAuthority('PERM_STAFF_CREDENTIAL_MANAGE')")
  public ResponseEntity<StaffAccountPasswordResetResponse> resetPassword(
      @PathVariable Long id, Principal principal) {
    return ResponseEntity.ok(
        new StaffAccountPasswordResetResponse(
            adminStaffService.resetPassword(id, principal.getName())));
  }
}
