package com.kizuna.auth.api.store;

import com.kizuna.auth.api.dto.LoginRequest;
import com.kizuna.auth.api.dto.TenantRegisterRequest;
import com.kizuna.auth.api.dto.TenantRegisterResponse;
import com.kizuna.auth.application.TenantAuthService;
import com.kizuna.tenant.domain.Tenant;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class AuthController {

  private final TenantAuthService authService;

  @PostMapping("/login")
  @PermitAll
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req.getUsername(), req.getPassword()));
  }

  @PostMapping("/logout")
  @PermitAll
  public ResponseEntity<?> logout() {
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/init-admin-user")
  @PermitAll
  public ResponseEntity<TenantRegisterResponse> initializeAdminUser(
      @Valid @RequestBody TenantRegisterRequest tenantRegisterRequest) {
    Tenant tenant = authService.initializeAdminUser(tenantRegisterRequest);
    return ResponseEntity.ok(new TenantRegisterResponse(tenant.getDomain(), tenant.getName()));
  }
}
