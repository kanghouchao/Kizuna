package com.kizuna.auth.api.platform;

import com.kizuna.auth.api.dto.PasswordChangeRequest;
import com.kizuna.auth.api.dto.PlatformLoginRequest;
import com.kizuna.auth.api.dto.PlatformMeResponse;
import com.kizuna.auth.api.dto.PlatformMeUpdateRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.application.AuthSessionService;
import com.kizuna.auth.application.PlatformAuthService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 統一（プラットフォーム）認証のコントローラ。 */
@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
public class PlatformAuthController {

  private final PlatformAuthService authService;
  private final AuthSessionService authSessionService;

  @PostMapping("/login")
  @PermitAll
  public ResponseEntity<Token> login(@Valid @RequestBody PlatformLoginRequest req) {
    return ResponseEntity.ok(authService.login(req.getEmail(), req.getPassword()));
  }

  @PostMapping("/logout")
  @PermitAll
  public ResponseEntity<?> logout(
      @RequestHeader(name = "Authorization", required = false) String authHeader) {
    authSessionService.invalidate(authHeader);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<PlatformMeResponse> me(Principal principal) {
    if (principal == null || principal.getName() == null) {
      return ResponseEntity.status(401).build();
    }
    return authService
        .me(principal.getName())
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(404).build());
  }

  @PutMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<PlatformMeResponse> updateMe(
      Principal principal, @Valid @RequestBody PlatformMeUpdateRequest req) {
    return ResponseEntity.ok(authService.updateMe(principal.getName(), req.getDisplayName()));
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
}
