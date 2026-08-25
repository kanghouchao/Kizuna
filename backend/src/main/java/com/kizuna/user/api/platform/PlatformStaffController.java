package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.application.PlatformStaffService;
import jakarta.validation.Valid;
import java.security.Principal;
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
 * 管理者管理 API（HQ 側ロール保持者のロール×店舗集合）。全操作 ROLE_MANAGE 権限限定。
 *
 * <p>店舗側ロールのみの利用者は本 API の対象外で、店舗スタッフ管理（STAFF_MANAGE 門）が扱う（ADR 0020）。
 */
@RestController
@RequestMapping("/platform/staff")
@RequiredArgsConstructor
public class PlatformStaffController {

  private final PlatformStaffService platformStaffService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<Page<PlatformStaffResponse>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Long storeId,
      @PageableDefault(sort = "displayName") Pageable pageable) {
    return ResponseEntity.ok(platformStaffService.list(search, storeId, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(platformStaffService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> create(
      @Valid @RequestBody PlatformStaffCreateRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(platformStaffService.create(req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> update(
      @PathVariable Long id,
      @Valid @RequestBody PlatformStaffUpdateRequest req,
      Principal principal) {
    return ResponseEntity.ok(platformStaffService.update(id, req, principal.getName()));
  }
}
