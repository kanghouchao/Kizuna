package com.kizuna.controller.tenant;

import com.kizuna.model.dto.tenant.TenantRegisterRequest;
import com.kizuna.model.dto.tenant.TenantRegisterResponse;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.service.central.tenant.TenantRegistrationService;
import com.kizuna.service.tenant.TenantAuthService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantRegistrationController {

  private final TenantRegistrationService registrationService;
  private final TenantAuthService tenantAuthService;

  @PostMapping("/register")
  @PermitAll
  public ResponseEntity<TenantRegisterResponse> register(
      @Valid @RequestBody TenantRegisterRequest tenantRegisterRequest) {
    Long tenantId = registrationService.validate(tenantRegisterRequest.getToken());
    Tenant tenant = tenantAuthService.register(tenantId, tenantRegisterRequest);
    registrationService.consume(tenantRegisterRequest.getToken());
    return ResponseEntity.ok(new TenantRegisterResponse(tenant.getDomain(), tenant.getName()));
  }
}
