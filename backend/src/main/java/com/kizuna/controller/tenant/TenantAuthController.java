package com.kizuna.controller.tenant;

import com.kizuna.model.dto.auth.LoginRequest;
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
public class TenantAuthController {

  private final TenantAuthService authService;

  @PostMapping("/login")
  @PermitAll
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req.getUsername(), req.getPassword()));
  }
}
