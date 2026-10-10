package com.kizuna.advertising.api.store;

import com.kizuna.advertising.application.AdvertisingInput;
import com.kizuna.shared.exception.ServiceException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.MultiValueMap;

record AdvertisingSummaryQuery(String month, PageRequest page, String format) {
  static AdvertisingSummaryQuery parse(MultiValueMap<String, String> params, boolean export) {
    var allowed = export ? Set.of("month", "format") : Set.of("month", "page", "size");
    if (params.entrySet().stream()
        .anyMatch(e -> !allowed.contains(e.getKey()) || e.getValue().size() != 1))
      throw new ServiceException("要求の項目または重複した指定を確認してください");
    String month = AdvertisingInput.month(params.getFirst("month"));
    if (export) return new AdvertisingSummaryQuery(month, null, params.getFirst("format"));
    int page = integer(params.getFirst("page"), 0);
    int size = integer(params.getFirst("size"), 20);
    if (size < 1) throw new ServiceException("ページ番号と件数を確認してください");
    return new AdvertisingSummaryQuery(month, PageRequest.of(page, Math.min(size, 100)), null);
  }

  private static int integer(String value, int fallback) {
    if (value == null) return fallback;
    try {
      if (!value.matches("[0-9]+")) throw new NumberFormatException();
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      throw new ServiceException("ページ番号と件数を確認してください");
    }
  }
}
