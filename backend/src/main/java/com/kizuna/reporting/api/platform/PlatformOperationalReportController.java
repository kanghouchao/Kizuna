package com.kizuna.reporting.api.platform;

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
@RequestMapping("/platform/operational-reports")
@RequiredArgsConstructor
public class PlatformOperationalReportController {
  private final OperationalReportService service;

  @GetMapping
  @PreAuthorize(
      "(!#includeRemuneration or hasAuthority('PERM_REMUNERATION_VIEW')) and hasAuthority('PERM_ORDER_SET_MANAGE') and hasAuthority('PERM_OPERATIONAL_REPORT_VIEW')")
  public ResponseEntity<OperationalReportResponse> view(
      @RequestParam(name = "store_id", required = false) Long storeId,
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(name = "group_by", defaultValue = "day") String groupBy,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(name = "include_remuneration", defaultValue = "false")
          boolean includeRemuneration) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.view(true, storeId, from, to, groupBy, page, size, includeRemuneration));
  }

  @GetMapping("/exports")
  @PreAuthorize(
      "(!#includeRemuneration or hasAuthority('PERM_REMUNERATION_VIEW')) and hasAuthority('PERM_ORDER_SET_MANAGE') and hasAuthority('PERM_OPERATIONAL_REPORT_VIEW') and hasAuthority('PERM_OPERATIONAL_REPORT_EXPORT')")
  public ResponseEntity<byte[]> export(
      @RequestParam(name = "store_id", required = false) Long storeId,
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(name = "group_by", defaultValue = "day") String groupBy,
      @RequestParam String format,
      @RequestParam(name = "include_remuneration", defaultValue = "false")
          boolean includeRemuneration)
      throws IOException {
    return ReportDownload.response(
        format, service.export(true, storeId, from, to, groupBy, format, includeRemuneration));
  }
}
