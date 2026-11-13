package com.kizuna.dashboard.api;

import com.kizuna.dashboard.api.dto.SalesResponse;
import com.kizuna.dashboard.application.SalesService;
import com.kizuna.shared.storescope.CurrentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 店舗のダッシュボードの売上区画。区画の権限だけで開き、運営集計の権限を求めない。 */
@RestController
@RequestMapping("/dashboard/sales")
@RequiredArgsConstructor
public class SalesController {
  private final SalesService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_DASHBOARD_SALES_VIEW')")
  public ResponseEntity<SalesResponse> view(@CurrentStore Long storeId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(SalesResponse.from(service.read(storeId)));
  }
}
