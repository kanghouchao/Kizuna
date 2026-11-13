package com.kizuna.dashboard.api;

import com.kizuna.dashboard.api.dto.StoresResponse;
import com.kizuna.dashboard.application.StoresService;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** グループのダッシュボードの「担当店舗」区画。区画の権限だけで開き、店舗の管理の権限を求めない。 */
@RestController
@RequestMapping("/dashboard/stores")
@RequiredArgsConstructor
public class StoresController {
  private final StoresService service;
  private final StoreScopeHolder storeScopeHolder;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_DASHBOARD_STORES_VIEW')")
  public ResponseEntity<StoresResponse> view() {
    StoreScope scope = storeScopeHolder.current();
    if (scope == null) throw new AccessDeniedException("店舗集合を解決できません");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(StoresResponse.from(service.read(scope)));
  }
}
