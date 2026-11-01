package com.kizuna.reporting.api;

import com.kizuna.reporting.api.dto.OperationalReportResponse;
import com.kizuna.reporting.application.OperationalReportService;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operational-reports/cross-store")
@RequiredArgsConstructor
public class CrossStoreOperationalReportController {
  private final OperationalReportService service;
  private final StoreScopeHolder storeScopeHolder;

  @GetMapping
  @PreAuthorize(
      "(!#includeAdvertising or hasAuthority('PERM_ADVERTISING_COST_SET_VIEW')) and (!#includeRemuneration or hasAuthority('PERM_REMUNERATION_VIEW')) and hasAuthority('PERM_ORDER_SET_VIEW') and hasAuthority('PERM_OPERATIONAL_REPORT_VIEW')")
  public ResponseEntity<OperationalReportResponse> view(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(name = "group_by", defaultValue = "day") String groupBy,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable,
      @RequestParam(name = "include_remuneration", defaultValue = "false")
          boolean includeRemuneration,
      @RequestParam(name = "include_advertising", defaultValue = "false")
          boolean includeAdvertising) {
    StoreScope scope = storeScopeHolder.current();
    if (scope == null) throw new AccessDeniedException("店舗集合スコープを解決できません");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            service.viewGroupWide(
                scope, from, to, groupBy, pageable, includeRemuneration, includeAdvertising));
  }

  @GetMapping("/exports")
  @PreAuthorize(
      "(!#includeAdvertising or hasAuthority('PERM_ADVERTISING_COST_SET_VIEW')) and (!#includeRemuneration or hasAuthority('PERM_REMUNERATION_VIEW')) and hasAuthority('PERM_ORDER_SET_VIEW') and hasAuthority('PERM_OPERATIONAL_REPORT_VIEW') and hasAuthority('PERM_OPERATIONAL_REPORT_EXPORT') and (!#includeAdvertising or hasAuthority('PERM_ADVERTISING_COST_SET_EXPORT'))")
  public ResponseEntity<byte[]> export(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(name = "group_by", defaultValue = "day") String groupBy,
      @RequestParam String format,
      @RequestParam(name = "include_remuneration", defaultValue = "false")
          boolean includeRemuneration,
      @RequestParam(name = "include_advertising", defaultValue = "false")
          boolean includeAdvertising)
      throws IOException {
    StoreScope scope = storeScopeHolder.current();
    if (scope == null) throw new AccessDeniedException("店舗集合スコープを解決できません");
    return ReportDownload.response(
        format,
        service.exportGroupWide(
            scope, from, to, groupBy, format, includeRemuneration, includeAdvertising));
  }
}
