package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.StaffAccountPasswordResetResponse;
import com.kizuna.user.application.PlatformStaffService;
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
 * 従業員管理 API。一覧・作成・授権変更は STORE_STAFF_MANAGE、停止・再開・パスワード代理再設定は STAFF_ACCOUNT_MANAGE 限定。
 * どの行使者も自分の店舗集合に収まる従業員だけを見て、自分の権限と店舗集合の部分集合だけを配れる。
 */
@RestController
@RequestMapping("/platform/staff")
@RequiredArgsConstructor
public class PlatformStaffController {

  private final PlatformStaffService platformStaffService;

  /** 一覧は行使者の店舗集合と交わる者に絞る。並びは全順序（表示名 + id）— 副キーが無いと更新のたびに行が頁を跨いで滑る。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<Page<PlatformStaffResponse>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Long storeId,
      @PageableDefault(sort = {"displayName", "id"}) Pageable pageable,
      Principal principal) {
    return ResponseEntity.ok(
        platformStaffService.list(search, storeId, pageable, principal.getName()));
  }

  /** 行使者が付与できるロールの目録。有界（ロール目録）なので裸の List で返す。 */
  @GetMapping("/grantable-roles")
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<List<RoleSummaryResponse>> grantableRoles(Principal principal) {
    return ResponseEntity.ok(platformStaffService.grantableRoles(principal.getName()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> get(@PathVariable Long id, Principal principal) {
    return ResponseEntity.ok(platformStaffService.get(id, principal.getName()));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> create(
      @Valid @RequestBody PlatformStaffCreateRequest req, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(platformStaffService.create(req, principal.getName()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> update(
      @PathVariable Long id,
      @Valid @RequestBody PlatformStaffUpdateRequest req,
      Principal principal) {
    return ResponseEntity.ok(platformStaffService.update(id, req, principal.getName()));
  }

  @PostMapping("/{id}/suspension")
  @PreAuthorize("hasAuthority('PERM_STAFF_ACCOUNT_MANAGE')")
  public ResponseEntity<Void> suspend(@PathVariable Long id, Principal principal) {
    platformStaffService.suspend(id, principal.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/resumption")
  @PreAuthorize("hasAuthority('PERM_STAFF_ACCOUNT_MANAGE')")
  public ResponseEntity<Void> resume(@PathVariable Long id, Principal principal) {
    platformStaffService.resume(id, principal.getName());
    return ResponseEntity.noContent().build();
  }

  /** 仮パスワードを発行して再設定する。生値を返すのはこの応答だけで、資源を作らないので 200 を返す。 */
  @PostMapping("/{id}/password-reset")
  @PreAuthorize("hasAuthority('PERM_STAFF_ACCOUNT_MANAGE')")
  public ResponseEntity<StaffAccountPasswordResetResponse> resetPassword(
      @PathVariable Long id, Principal principal) {
    return ResponseEntity.ok(
        new StaffAccountPasswordResetResponse(
            platformStaffService.resetPassword(id, principal.getName())));
  }
}
