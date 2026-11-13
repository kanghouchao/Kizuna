package com.kizuna.dashboard.api;

import com.kizuna.dashboard.api.dto.CrossStoreSalesResponse;
import com.kizuna.dashboard.application.SalesService;
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

/** グループのダッシュボードの店舗集合の売上区画。区画の権限だけで開き、横断の運営集計の権限を求めない。 */
@RestController
@RequestMapping("/dashboard/sales/cross-store")
@RequiredArgsConstructor
public class CrossStoreSalesController {
  private final SalesService service;
  private final StoreScopeHolder storeScopeHolder;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_DASHBOARD_SALES_SET_VIEW')")
  public ResponseEntity<CrossStoreSalesResponse> view() {
    StoreScope scope = storeScopeHolder.current();
    if (scope == null) throw new AccessDeniedException("店舗集合を解決できません");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(CrossStoreSalesResponse.from(service.readGroupWide(scope)));
  }
}
