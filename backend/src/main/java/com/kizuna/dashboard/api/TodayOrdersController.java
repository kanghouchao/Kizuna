package com.kizuna.dashboard.api;

import com.kizuna.dashboard.api.dto.TodayOrdersResponse;
import com.kizuna.dashboard.application.TodayOrdersService;
import com.kizuna.shared.storescope.CurrentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 店舗のダッシュボードの「今日の受注」区画。区画の権限だけで開き、受注の一覧の権限を求めない。 */
@RestController
@RequestMapping("/dashboard/today-orders")
@RequiredArgsConstructor
public class TodayOrdersController {
  private final TodayOrdersService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_DASHBOARD_TODAY_ORDERS_VIEW')")
  public ResponseEntity<TodayOrdersResponse> view(@CurrentStore Long storeId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(TodayOrdersResponse.from(service.read(storeId)));
  }
}
