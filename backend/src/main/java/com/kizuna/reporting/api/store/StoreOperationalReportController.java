package com.kizuna.reporting.api.store;

import com.kizuna.reporting.api.ReportDownload;
import com.kizuna.reporting.api.dto.OperationalReportResponse;
import com.kizuna.reporting.application.OperationalReportService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/operational-reports")
@RequiredArgsConstructor
public class StoreOperationalReportController {
  private final OperationalReportService service;

  @GetMapping
  @PreAuthorize(
      "(!#includeAdvertising or hasAuthority('PERM_ADVERTISING_COST_VIEW')) and (!#includeRemuneration or hasAuthority('PERM_REMUNERATION_VIEW')) and hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_OPERATIONAL_REPORT_VIEW')")
  public ResponseEntity<OperationalReportResponse> view(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(name = "group_by", defaultValue = "day") String groupBy,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(name = "include_remuneration", defaultValue = "false")
          boolean includeRemuneration,
      @RequestParam(name = "include_advertising", defaultValue = "false")
          boolean includeAdvertising) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            service.view(
                false,
                null,
                from,
                to,
                groupBy,
                page,
                size,
                includeRemuneration,
                includeAdvertising));
  }

  @GetMapping("/exports")
  @PreAuthorize(
      "(!#includeAdvertising or hasAuthority('PERM_ADVERTISING_COST_VIEW')) and (!#includeRemuneration or hasAuthority('PERM_REMUNERATION_VIEW')) and hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_OPERATIONAL_REPORT_VIEW') and hasAuthority('PERM_OPERATIONAL_REPORT_EXPORT') and (!#includeAdvertising or hasAuthority('PERM_ADVERTISING_COST_EXPORT'))")
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
    return ReportDownload.response(
        format,
        service.export(
            false, null, from, to, groupBy, format, includeRemuneration, includeAdvertising));
  }
}
