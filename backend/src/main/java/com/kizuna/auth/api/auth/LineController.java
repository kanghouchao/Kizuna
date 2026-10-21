package com.kizuna.auth.api.auth;

import com.kizuna.auth.api.dto.LineAuthorizationRequest;
import com.kizuna.auth.api.dto.LineConfigResponse;
import com.kizuna.auth.api.dto.LineLoginResponse;
import com.kizuna.auth.api.dto.LineRegistrationRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.application.LineAuthService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * LINE を認証手段とする端点。認可コードの交換はバックエンドで行い、チャネルシークレットは前端へ出さない。
 *
 * <p>設定・ログイン・登録は匿名で叩かれる公開端点（SecurityConfig の CSRF 免除と ExemptingBearerTokenResolver の Bearer 免除の対象）。
 * 連携（POST /me/line）だけは Bearer を要求し、現在の認証主体に結び付ける。
 */
@RestController
@RequiredArgsConstructor
public class LineController {

  private final LineAuthService lineAuthService;

  @GetMapping("/auth/line/config")
  @PermitAll
  public ResponseEntity<LineConfigResponse> config() {
    return ResponseEntity.ok(lineAuthService.config());
  }

  @PostMapping("/auth/line/login")
  @PermitAll
  public ResponseEntity<LineLoginResponse> login(
      @Valid @RequestBody LineAuthorizationRequest request) {
    return ResponseEntity.ok(lineAuthService.login(request));
  }

  @PostMapping("/auth/line/register")
  @PermitAll
  public ResponseEntity<Token> register(@Valid @RequestBody LineRegistrationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(lineAuthService.register(request));
  }

  @PostMapping("/me/line")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> link(
      Principal principal, @Valid @RequestBody LineAuthorizationRequest request) {
    lineAuthService.link(principal.getName(), request);
    return ResponseEntity.noContent().build();
  }
}
