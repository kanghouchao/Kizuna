package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.application.PlatformStaffService;
import jakarta.validation.Valid;
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

/** 平台スタッフ（ロール×店舗集合）管理 API。全操作 HQ 限定（#325）。 */
@RestController
@RequestMapping("/platform/staff")
@RequiredArgsConstructor
public class PlatformStaffController {

  private final PlatformStaffService platformStaffService;

  @GetMapping
  @PreAuthorize("hasAuthority('ROLE_HQ_ADMIN')")
  public ResponseEntity<List<PlatformStaffResponse>> list() {
    return ResponseEntity.ok(platformStaffService.list());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('ROLE_HQ_ADMIN')")
  public ResponseEntity<PlatformStaffResponse> create(
      @Valid @RequestBody PlatformStaffCreateRequest req) {
    return ResponseEntity.ok(platformStaffService.create(req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('ROLE_HQ_ADMIN')")
  public ResponseEntity<PlatformStaffResponse> update(
      @PathVariable Long id, @Valid @RequestBody PlatformStaffUpdateRequest req) {
    return platformStaffService
        .update(id, req)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
