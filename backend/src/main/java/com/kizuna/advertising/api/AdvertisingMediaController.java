package com.kizuna.advertising.api;

import com.kizuna.advertising.api.dto.AdvertisingMediaSummaryResponse;
import com.kizuna.advertising.application.AdvertisingExportService;
import com.kizuna.advertising.application.AdvertisingMediaService;
import com.kizuna.shared.storescope.CurrentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/advertising-media-summaries")
@RequiredArgsConstructor
public class AdvertisingMediaController {
  private final AdvertisingMediaService service;
  private final AdvertisingExportService exports;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<AdvertisingMediaSummaryResponse> list(
      @CurrentStore Long storeId,
      @RequestParam MultiValueMap<String, String> params,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    var query = AdvertisingSummaryQuery.parse(params, false);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AdvertisingMediaSummaryResponse.of(service.view(storeId, query.month()), pageable));
  }

  @GetMapping("/exports")
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_EXPORT')")
  public ResponseEntity<byte[]> export(
      @CurrentStore Long storeId, @RequestParam MultiValueMap<String, String> params) {
    var query = AdvertisingSummaryQuery.parse(params, true);
    String month = query.month();
    String format = query.format();
    var bytes = exports.exportMedia(storeId, month, format);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(
            MediaType.parseMediaType(
                "csv".equals(format)
                    ? "text/csv;charset=UTF-8"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"advertising-media-summaries-"
                + storeId
                + "-"
                + month
                + "."
                + format
                + "\"")
        .body(bytes);
  }
}
