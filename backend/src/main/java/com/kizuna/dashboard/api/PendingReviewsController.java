package com.kizuna.dashboard.api;

import com.kizuna.dashboard.api.dto.PendingCountResponse;
import com.kizuna.dashboard.application.PendingWorkService;
import com.kizuna.shared.storescope.CurrentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 店舗のダッシュボードの「審査待ちの口コミ」区画。区画の権限だけで開き、口コミの閲覧の権限を求めない。 */
@RestController
@RequestMapping("/dashboard/pending-reviews")
@RequiredArgsConstructor
public class PendingReviewsController {
  private final PendingWorkService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_DASHBOARD_PENDING_REVIEWS_VIEW')")
  public ResponseEntity<PendingCountResponse> view(@CurrentStore Long storeId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new PendingCountResponse(service.reviews(storeId)));
  }
}
