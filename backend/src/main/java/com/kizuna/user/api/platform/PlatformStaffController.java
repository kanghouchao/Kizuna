package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.application.PlatformStaffService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
}
