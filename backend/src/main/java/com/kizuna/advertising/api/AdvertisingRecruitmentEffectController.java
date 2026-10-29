package com.kizuna.advertising.api;

import com.kizuna.advertising.api.dto.AdvertisingRecruitmentEffectResponse;
import com.kizuna.advertising.application.AdvertisingRecruitmentEffectService;
import com.kizuna.shared.storescope.CurrentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/advertising-recruitment-effects")
@RequiredArgsConstructor
public class AdvertisingRecruitmentEffectController {
  private final AdvertisingRecruitmentEffectService service;

  @GetMapping
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_RECRUITMENT_VIEW')")
  public ResponseEntity<AdvertisingRecruitmentEffectResponse> list(
      @CurrentStore Long storeId, @RequestParam MultiValueMap<String, String> params) {
    var query = AdvertisingSummaryQuery.parse(params, false);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            AdvertisingRecruitmentEffectResponse.of(
                service.view(storeId, query.month()), query.page()));
  }
}
