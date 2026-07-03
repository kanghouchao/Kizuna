package com.kizuna.auth.api.store;

import com.kizuna.auth.api.dto.LoginRequest;
import com.kizuna.auth.api.dto.PasswordChangeRequest;
import com.kizuna.auth.api.dto.TenantRegisterRequest;
import com.kizuna.auth.api.dto.TenantRegisterResponse;
import com.kizuna.auth.application.AuthSessionService;
import com.kizuna.auth.application.TenantAuthService;
import com.kizuna.tenant.domain.Tenant;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class AuthController {

  private final TenantAuthService authService;
  private final AuthSessionService authSessionService;

  @PostMapping("/login")
  @PermitAll
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req.getUsername(), req.getPassword()));
  }

  @PostMapping("/logout")
  @PermitAll
  public ResponseEntity<?> logout(
      @RequestHeader(name = "Authorization", required = false) String authHeader) {
    authSessionService.invalidate(authHeader);
    return ResponseEntity.noContent().build();
  }

  /** パスワード変更。成功時は現在のトークンを失効させるため、クライアントは再ログインが必要。 */
  @PutMapping("/password")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> changePassword(
      Principal principal,
      @RequestHeader(name = "Authorization", required = false) String authHeader,
      @Valid @RequestBody PasswordChangeRequest request) {
    authService.changePassword(
        principal.getName(), request.getCurrentPassword(), request.getNewPassword(), authHeader);
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
