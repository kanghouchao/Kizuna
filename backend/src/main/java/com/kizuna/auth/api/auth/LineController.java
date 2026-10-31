package com.kizuna.auth.api.auth;

import com.kizuna.auth.api.dto.LineAuthorizationRequest;
import com.kizuna.auth.api.dto.LineConfigResponse;
import com.kizuna.auth.api.dto.LineLoginResponse;
import com.kizuna.auth.api.dto.LineRegistrationRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.application.LineAuthService;
import com.kizuna.auth.application.LineLogin;
import com.kizuna.auth.infrastructure.RefreshTokenCookies;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
 * <p>設定・ログイン・登録は匿名で叩かれる公開端点（ExemptingBearerTokenResolver の Bearer 免除の対象）。 連携（POST /me/line）だけは
 * Bearer を要求し、現在の認証主体に結び付ける。
 */
@RestController
@RequiredArgsConstructor
public class LineController {

  private final LineAuthService lineAuthService;
  private final RefreshTokenCookies refreshTokenCookies;

  @GetMapping("/auth/line/config")
  @PermitAll
  public ResponseEntity<LineConfigResponse> config() {
    return ResponseEntity.ok(lineAuthService.config());
  }

  @PostMapping("/auth/line/login")
  @PermitAll
  public ResponseEntity<LineLoginResponse> login(
      @Valid @RequestBody LineAuthorizationRequest request) {
    LineLogin login = lineAuthService.login(request);
    if (login.session() == null) {
      return ResponseEntity.ok(
          LineLoginResponse.unregistered(login.registrationTicket(), login.displayName()));
    }
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE, refreshTokenCookies.issue(login.session().refresh()).toString())
        .body(LineLoginResponse.registered(login.session().toToken()));
  }

  @PostMapping("/auth/line/register")
  @PermitAll
  public ResponseEntity<Token> register(@Valid @RequestBody LineRegistrationRequest request) {
    return AuthController.withRefreshCookie(
        ResponseEntity.status(HttpStatus.CREATED),
        lineAuthService.register(request),
        refreshTokenCookies);
  }

  @PostMapping("/me/line")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> link(
      Principal principal, @Valid @RequestBody LineAuthorizationRequest request) {
    lineAuthService.link(principal.getName(), request);
    return ResponseEntity.noContent().build();
  }
}
