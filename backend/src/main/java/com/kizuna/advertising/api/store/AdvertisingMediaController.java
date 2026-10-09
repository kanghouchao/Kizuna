package com.kizuna.advertising.api.store;

import com.kizuna.advertising.api.dto.AdvertisingMediaSummaryResponse;
import com.kizuna.advertising.application.AdvertisingExportService;
import com.kizuna.advertising.application.AdvertisingInput;
import com.kizuna.advertising.application.AdvertisingMediaService;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
@RequestMapping("/store/advertising-media-summaries")
@RequiredArgsConstructor
public class AdvertisingMediaController {
  private final AdvertisingMediaService service;
  private final AdvertisingExportService exports;
  private final StoreContext stores;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<AdvertisingMediaSummaryResponse> list(
      @RequestParam MultiValueMap<String, String> params) {
    validate(params, Set.of("month", "page", "size"));
    String month = AdvertisingInput.month(params.getFirst("month"));
    int page = integer(params.getFirst("page"), 0);
    int size = integer(params.getFirst("size"), 20);
    if (size < 1) throw new ServiceException("ページ番号と件数を確認してください");
    var paging = PageRequest.of(page, Math.min(size, 100));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AdvertisingMediaSummaryResponse.of(service.view(month), paging));
  }

  @GetMapping("/exports")
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_EXPORT')")
  public ResponseEntity<byte[]> export(@RequestParam MultiValueMap<String, String> params) {
    validate(params, Set.of("month", "format"));
    String month = AdvertisingInput.month(params.getFirst("month"));
    String format = params.getFirst("format");
    var bytes = exports.exportMedia(month, format);
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
                + stores.getStoreId()
                + "-"
                + month
                + "."
                + format
                + "\"")
        .body(bytes);
  }

  private void validate(MultiValueMap<String, String> params, Set<String> allowed) {
    if (params.entrySet().stream()
        .anyMatch(e -> !allowed.contains(e.getKey()) || e.getValue().size() != 1))
      throw new ServiceException("要求の項目または重複した指定を確認してください");
  }

  private int integer(String value, int fallback) {
    if (value == null) return fallback;
    try {
      if (!value.matches("[0-9]+")) throw new NumberFormatException();
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      throw new ServiceException("ページ番号と件数を確認してください");
    }
  }
}
