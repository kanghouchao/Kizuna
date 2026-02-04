package com.kizuna.controller.tenant;

import com.kizuna.model.dto.tenant.siteconfig.SiteConfigResponse;
import com.kizuna.model.dto.tenant.siteconfig.SiteConfigUpdateRequest;
import com.kizuna.service.tenant.SiteConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/site-config")
@RequiredArgsConstructor
public class SiteConfigController {

  private final SiteConfigService siteConfigService;

  @GetMapping
  public ResponseEntity<SiteConfigResponse> get() {
    return ResponseEntity.ok(siteConfigService.get());
  }

  @PutMapping
  public ResponseEntity<SiteConfigResponse> update(
      @Valid @RequestBody SiteConfigUpdateRequest request) {
    return ResponseEntity.ok(siteConfigService.update(request));
  }
}
