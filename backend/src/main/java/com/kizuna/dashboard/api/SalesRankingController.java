package com.kizuna.dashboard.api;

import com.kizuna.dashboard.api.dto.SalesRankingResponse;
import com.kizuna.dashboard.application.SalesRankingService;
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

/** グループのダッシュボードの店舗別の売上順位。区画の権限だけで開き、運営集計の権限を求めない。一覧は店舗集合の店舗数が上限。 */
@RestController
@RequestMapping("/dashboard/sales-ranking")
@RequiredArgsConstructor
public class SalesRankingController {
  private final SalesRankingService service;
  private final StoreScopeHolder storeScopeHolder;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_DASHBOARD_SALES_RANKING_VIEW')")
  public ResponseEntity<SalesRankingResponse> view() {
    StoreScope scope = storeScopeHolder.current();
    if (scope == null) throw new AccessDeniedException("店舗集合を解決できません");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(SalesRankingResponse.from(service.read(scope)));
  }
}
