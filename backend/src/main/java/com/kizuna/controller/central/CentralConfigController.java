package com.kizuna.controller.central;

import com.kizuna.model.dto.central.config.SystemConfigResponse;
import com.kizuna.model.dto.central.config.SystemConfigUpdateRequest;
import com.kizuna.service.central.config.SystemConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
  @RolesAllowed("ADMIN")
  public ResponseEntity<List<SystemConfigResponse>> list(
      @RequestParam(required = false) String category) {
    if (category != null && !category.isBlank()) {
      return ResponseEntity.ok(systemConfigService.getConfigsByCategory(category));
    }
    return ResponseEntity.ok(systemConfigService.getAllConfigs());
  }

  @PutMapping
  @RolesAllowed("ADMIN")
  public ResponseEntity<SystemConfigResponse> update(
      @Valid @RequestBody SystemConfigUpdateRequest request) {
    return ResponseEntity.ok(systemConfigService.updateConfig(request));
  }
}
