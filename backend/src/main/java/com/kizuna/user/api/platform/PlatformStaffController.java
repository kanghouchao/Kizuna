package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.GrantHistoryEntryResponse;
import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.application.PlatformStaffService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台スタッフ（能力束×店舗集合×精算範囲）管理 API。全操作 STAFF_MANAGE 能力限定。 */
@RestController
@RequestMapping("/platform/staff")
@RequiredArgsConstructor
public class PlatformStaffController {

  private final PlatformStaffService platformStaffService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<List<PlatformStaffResponse>> list() {
    return ResponseEntity.ok(platformStaffService.list());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> create(
      @Valid @RequestBody PlatformStaffCreateRequest req, Principal principal) {
    return ResponseEntity.ok(platformStaffService.create(req, principal.getName()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<PlatformStaffResponse> update(
      @PathVariable Long id,
      @Valid @RequestBody PlatformStaffUpdateRequest req,
      Principal principal) {
    return platformStaffService
        .update(id, req, principal.getName())
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}/grant-history")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<List<GrantHistoryEntryResponse>> grantHistory(@PathVariable Long id) {
    return platformStaffService
        .grantHistory(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
