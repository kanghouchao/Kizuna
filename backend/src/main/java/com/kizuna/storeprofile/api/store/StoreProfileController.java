package com.kizuna.storeprofile.api.store;

import com.kizuna.storeprofile.api.dto.StoreProfileResponse;
import com.kizuna.storeprofile.api.dto.StoreProfileUpdateRequest;
import com.kizuna.storeprofile.application.StoreProfileService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/config")
@RequiredArgsConstructor
public class StoreProfileController {

  private final StoreProfileService tenantConfigService;

  @GetMapping
  @PreAuthorize("hasAuthority('TENANT_CONFIG')")
  public ResponseEntity<StoreProfileResponse> get() {
    return ResponseEntity.ok(tenantConfigService.get());
  }

  @PutMapping
  @PreAuthorize("hasAuthority('TENANT_CONFIG')")
  public ResponseEntity<StoreProfileResponse> update(
      @Valid @RequestBody StoreProfileUpdateRequest request) {
    return ResponseEntity.ok(tenantConfigService.update(request));
  }

  @GetMapping("/public")
  @PermitAll
  public ResponseEntity<StoreProfileResponse> getPublic() {
    return ResponseEntity.ok(tenantConfigService.get());
  }
}
