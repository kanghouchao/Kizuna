package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.StaffAccountPasswordResetResponse;
import com.kizuna.user.api.dto.StaffAccountResponse;
import com.kizuna.user.api.dto.StaffAccountSummaryResponse;
import com.kizuna.user.application.PlatformStaffAccountService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * アカウント管理 API（本人種別 STAFF の全アカウントの閲覧・停止・再開・パスワード再設定）。全操作 STAFF_ACCOUNT_MANAGE 権限限定。
 *
 * <p>授権を書く口はこの面に存在しない。ロールと店舗集合の変更は管理者管理（/platform/staff）と店舗スタッフ管理が引き続き担う。
 */
@RestController
@RequestMapping("/platform/staff-accounts")
@RequiredArgsConstructor
public class PlatformStaffAccountController {

  private final PlatformStaffAccountService platformStaffAccountService;

  /** 既定の並びに id を添えるのは、offset ページングが全順序を要求するためである（同名の行が頁を跨いで滑らない）。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_ACCOUNT_MANAGE')")
  public ResponseEntity<Page<StaffAccountSummaryResponse>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Long storeId,
      @PageableDefault(sort = {"displayName", "id"}) Pageable pageable) {
    return ResponseEntity.ok(platformStaffAccountService.list(search, storeId, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_ACCOUNT_MANAGE')")
  public ResponseEntity<StaffAccountResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(platformStaffAccountService.get(id));
  }

  @PostMapping("/{id}/suspension")
  @PreAuthorize("hasAuthority('PERM_STAFF_ACCOUNT_MANAGE')")
  public ResponseEntity<Void> suspend(@PathVariable Long id, Principal principal) {
    platformStaffAccountService.suspend(id, principal.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/resumption")
  @PreAuthorize("hasAuthority('PERM_STAFF_ACCOUNT_MANAGE')")
  public ResponseEntity<Void> resume(@PathVariable Long id) {
    platformStaffAccountService.resume(id);
    return ResponseEntity.noContent().build();
  }

  /** 仮パスワードを発行して再設定する。生値を返すのはこの応答だけで、資源を作らないので 200 を返す。 */
  @PostMapping("/{id}/password-reset")
  @PreAuthorize("hasAuthority('PERM_STAFF_ACCOUNT_MANAGE')")
  public ResponseEntity<StaffAccountPasswordResetResponse> resetPassword(
      @PathVariable Long id, Principal principal) {
    return ResponseEntity.ok(
        new StaffAccountPasswordResetResponse(
            platformStaffAccountService.resetPassword(id, principal.getName())));
  }
}
