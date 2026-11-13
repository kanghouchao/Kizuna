package com.kizuna.dashboard.api;

import com.kizuna.dashboard.api.dto.PendingOrderApplicationsResponse;
import com.kizuna.dashboard.application.PendingWorkService;
import com.kizuna.shared.storescope.CurrentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 店舗のダッシュボードの「未処理の予約申請」区画。区画の権限だけで開き、受注の一覧の権限を求めない。 */
@RestController
@RequestMapping("/dashboard/pending-order-applications")
@RequiredArgsConstructor
public class PendingOrderApplicationsController {
  private final PendingWorkService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_DASHBOARD_PENDING_ORDER_APPLICATIONS_VIEW')")
  public ResponseEntity<PendingOrderApplicationsResponse> view(@CurrentStore Long storeId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(PendingOrderApplicationsResponse.from(service.orderApplications(storeId)));
  }
}
