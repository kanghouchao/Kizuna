package com.kizuna.tenant.api.platform;

import com.kizuna.tenant.api.dto.PlatformStoreResponse;
import com.kizuna.tenant.application.PlatformStoreService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台（統一）店舗 API。授権店舗一覧を提供する（#324 統一ログイン）。 */
@RestController
@RequestMapping("/platform/stores")
@RequiredArgsConstructor
public class PlatformStoreController {

  private final PlatformStoreService platformStoreService;

  @GetMapping
  @PreAuthorize("hasAnyAuthority('ROLE_HQ_ADMIN','ROLE_STORE_MANAGER','ROLE_STORE_STAFF')")
  public ResponseEntity<List<PlatformStoreResponse>> list() {
    return ResponseEntity.ok(platformStoreService.listAuthorizedStores());
  }
}
