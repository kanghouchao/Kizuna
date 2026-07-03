package com.kizuna.settings.api.central;

import com.kizuna.settings.api.dto.SystemConfigResponse;
import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.application.SystemConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/central/configs")
@RequiredArgsConstructor
public class CentralConfigController {

  private final SystemConfigService systemConfigService;

  @GetMapping
  @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
  public ResponseEntity<List<SystemConfigResponse>> list(
      @RequestParam(required = false) String category) {
    if (category != null && !category.isBlank()) {
      return ResponseEntity.ok(systemConfigService.getConfigsByCategory(category));
    }
    return ResponseEntity.ok(systemConfigService.getAllConfigs());
  }

  @PutMapping
  @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
  public ResponseEntity<SystemConfigResponse> update(
      @Valid @RequestBody SystemConfigUpdateRequest request) {
    return ResponseEntity.ok(systemConfigService.updateConfig(request));
  }
}
