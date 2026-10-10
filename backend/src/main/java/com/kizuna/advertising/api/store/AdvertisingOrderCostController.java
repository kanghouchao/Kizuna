package com.kizuna.advertising.api.store;

import com.kizuna.advertising.api.dto.AdvertisingOrderCostResponse;
import com.kizuna.advertising.application.AdvertisingExportService;
import com.kizuna.advertising.application.AdvertisingOrderCostService;
import com.kizuna.shared.storescope.StoreContext;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/store/advertising-order-costs")
@RequiredArgsConstructor
public class AdvertisingOrderCostController {
  private final AdvertisingOrderCostService service;
  private final AdvertisingExportService exports;
  private final StoreContext stores;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<AdvertisingOrderCostResponse> list(
      @RequestParam MultiValueMap<String, String> params) {
    var query = AdvertisingSummaryQuery.parse(params, false);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AdvertisingOrderCostResponse.of(service.view(query.month()), query.page()));
  }

  @GetMapping("/exports")
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_ADVERTISING_COST_EXPORT')")
  public ResponseEntity<byte[]> export(@RequestParam MultiValueMap<String, String> params) {
    var query = AdvertisingSummaryQuery.parse(params, true);
    String month = query.month();
    String format = query.format();
    var bytes = exports.exportOrderCost(month, format);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(
            MediaType.parseMediaType(
                "csv".equals(format)
                    ? "text/csv;charset=UTF-8"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"advertising-order-costs-"
                + stores.getStoreId()
                + "-"
                + month
                + "."
                + format
                + "\"")
        .body(bytes);
  }
}
