package com.kizuna.settings.api;

import com.kizuna.settings.api.dto.SystemConfigResponse;
import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.web.JwtClaims;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system-configs")
@RequiredArgsConstructor
public class SystemConfigController {

  private final SystemConfigService systemConfigService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SYSTEM_CONFIG_MANAGE')")
  public ResponseEntity<List<SystemConfigResponse>> list(
      @RequestParam(required = false) String category) {
    if (category != null && !category.isBlank()) {
      return ResponseEntity.ok(
          systemConfigService.getConfigsByCategory(category).stream()
              .map(SystemConfigResponse::of)
              .toList());
    }
    return ResponseEntity.ok(
        systemConfigService.getAllConfigs().stream().map(SystemConfigResponse::of).toList());
  }

  @PutMapping("/{key}")
  @PreAuthorize("hasAuthority('PERM_SYSTEM_CONFIG_MANAGE')")
  public ResponseEntity<SystemConfigResponse> update(
      @PathVariable String key,
      @Valid @RequestBody SystemConfigUpdateRequest request,
      Principal principal) {
    return ResponseEntity.ok(
        SystemConfigResponse.of(
            systemConfigService.updateConfig(
                JwtClaims.userIdOf(principal), key, request.getConfigValue())));
  }
}
