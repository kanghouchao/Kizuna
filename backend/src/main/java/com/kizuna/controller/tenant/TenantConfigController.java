package com.kizuna.controller.tenant;

import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigResponse;
import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigUpdateRequest;
import com.kizuna.service.tenant.TenantConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/config")
@RequiredArgsConstructor
public class TenantConfigController {

  private final TenantConfigService tenantConfigService;

  @GetMapping
  public ResponseEntity<TenantConfigResponse> get() {
    return ResponseEntity.ok(tenantConfigService.get());
  }

  @PutMapping
  public ResponseEntity<TenantConfigResponse> update(
      @Valid @RequestBody TenantConfigUpdateRequest request) {
    return ResponseEntity.ok(tenantConfigService.update(request));
  }
}
