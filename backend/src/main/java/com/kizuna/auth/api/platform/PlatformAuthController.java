package com.kizuna.auth.api.platform;

import com.kizuna.auth.api.dto.PlatformLoginRequest;
import com.kizuna.auth.api.dto.PlatformMeResponse;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.application.AuthSessionService;
import com.kizuna.auth.application.PlatformAuthService;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
  private final PlatformUserRepository userRepository;
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
    PlatformUser user = userRepository.findByEmail(principal.getName()).orElse(null);
    if (user == null) return ResponseEntity.status(404).build();
    return ResponseEntity.ok(
        new PlatformMeResponse(
            user.getEmail(),
            user.getDisplayName(),
            user.getRole().name(),
            user.getStoreScopeType().name(),
            user.getStoreIds().stream().sorted().toList()));
  }
}
