package com.kizuna.advertising.api;

import com.kizuna.advertising.application.AdvertisingInput;
import com.kizuna.shared.exception.ServiceException;
import java.util.Set;
import org.springframework.util.MultiValueMap;

/** 集計の要求項目を厳密に読む。page と size は Pageable が束縛し、並びは集計が決めるため sort は受け付けない。 */
record AdvertisingSummaryQuery(String month, String format) {
  static AdvertisingSummaryQuery parse(MultiValueMap<String, String> params, boolean export) {
    var allowed = export ? Set.of("month", "format") : Set.of("month", "page", "size");
    if (params.entrySet().stream()
        .anyMatch(e -> !allowed.contains(e.getKey()) || e.getValue().size() != 1))
      throw new ServiceException("要求の項目または重複した指定を確認してください");
    return new AdvertisingSummaryQuery(
        AdvertisingInput.month(params.getFirst("month")),
        export ? params.getFirst("format") : null);
  }
}
