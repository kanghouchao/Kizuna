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

/** 平台スタッフ（ロール×店舗集合）管理 API。全操作 STAFF_MANAGE 権限限定。 */
@RestController
@RequestMapping("/platform/staff")
@RequiredArgsConstructor
public class PlatformStaffController {

  private final PlatformStaffService platformStaffService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<Page<PlatformStaffResponse>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Long storeId,
      @PageableDefault(sort = "displayName") Pageable pageable) {
    return ResponseEntity.ok(platformStaffService.list(search, storeId, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(platformStaffService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> create(
      @Valid @RequestBody PlatformStaffCreateRequest req) {
    return ResponseEntity.ok(platformStaffService.create(req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> update(
      @PathVariable Long id,
      @Valid @RequestBody PlatformStaffUpdateRequest req,
      Principal principal) {
    return ResponseEntity.ok(platformStaffService.update(id, req, principal.getName()));
  }
}
