package com.kizuna.user.api.store;

import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.StoreStaffCreateRequest;
import com.kizuna.user.api.dto.StoreStaffResponse;
import com.kizuna.user.api.dto.StoreStaffUpdateRequest;
import com.kizuna.user.application.StoreStaffService;
import jakarta.validation.Valid;
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
 * 店舗スタッフ管理 API（店舗側ロールのみを持つアカウントのロール×店舗集合）。全操作 STORE_STAFF_MANAGE 権限限定で、既定でこれを持つのは店長だけである（ADR 0021
 * の撤退）。
 *
 * <p>HQ 側ロール保持者は本 API の対象外で、管理者管理（ROLE_MANAGE 門）が扱う。
 */
@RestController
@RequestMapping("/store/staff-members")
@RequiredArgsConstructor
public class StoreStaffController {

  private final StoreStaffService storeStaffService;

  /** 一覧は店舗コンテキストの店を担当範囲に含む者へ絞る。並びは全順序（表示名 + id）— 副キーが無いと更新のたびに行が頁を跨いで滑る。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<Page<StoreStaffResponse>> list(
      @RequestParam(required = false) String search,
      @PageableDefault(sort = {"displayName", "id"}) Pageable pageable) {
    return ResponseEntity.ok(storeStaffService.list(search, pageable));
  }

  /** 行使者が付与できるロールの目録。有界（ロール目録）なので裸の List で返す。 */
  @GetMapping("/grantable-roles")
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<List<RoleSummaryResponse>> grantableRoles() {
    return ResponseEntity.ok(storeStaffService.grantableRoles());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<StoreStaffResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(storeStaffService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<StoreStaffResponse> create(
      @Valid @RequestBody StoreStaffCreateRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(storeStaffService.create(req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_STAFF_MANAGE')")
  public ResponseEntity<StoreStaffResponse> update(
      @PathVariable Long id, @Valid @RequestBody StoreStaffUpdateRequest req) {
    return ResponseEntity.ok(storeStaffService.update(id, req));
  }
}
