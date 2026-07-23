package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.CapabilityBundleResponse;
import com.kizuna.user.application.PlatformStaffService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 能力束一覧 API（スタッフ授与 UI の選択肢データ源）。STAFF_MANAGE 能力限定。 */
@RestController
@RequestMapping("/platform/capability-bundles")
@RequiredArgsConstructor
public class CapabilityBundleController {

  private final PlatformStaffService platformStaffService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<List<CapabilityBundleResponse>> list() {
    return ResponseEntity.ok(platformStaffService.listBundles());
  }
}
