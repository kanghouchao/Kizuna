package com.kizuna.auth.api.auth;

import com.kizuna.auth.api.dto.LoginRequest;
import com.kizuna.auth.api.dto.MeResponse;
import com.kizuna.auth.api.dto.MeUpdateRequest;
import com.kizuna.auth.api.dto.PasswordChangeRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.application.AuthService;
import com.kizuna.auth.application.AuthSessionService;
import com.kizuna.auth.application.IssuedSession;
import com.kizuna.auth.infrastructure.RefreshTokenCookies;
import com.kizuna.auth.infrastructure.RefreshTokenRejectedException;
import com.kizuna.shared.exception.StaleSessionException;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 統一認証のコントローラ。ログイン・リフレッシュ・ログアウトは /auth、本人資料は /me に掛ける。 */
@RestController
@RequiredArgsConstructor
public class AuthController {

  static final String AUTH_FAILURE_HEADER = "X-Auth-Failure";

  private final AuthService authService;
  private final AuthSessionService authSessionService;
  private final RefreshTokenCookies refreshTokenCookies;

  @PostMapping("/auth/login")
  @PermitAll
  public ResponseEntity<Token> login(@Valid @RequestBody LoginRequest req) {
    return withRefreshCookie(
        ResponseEntity.ok(),
        authService.login(req.getEmail(), req.getPassword()),
        refreshTokenCookies);
  }

  @PostMapping("/auth/refresh")
  @PermitAll
  public ResponseEntity<Token> refresh(
      @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
      @RequestHeader(name = "X-Session-User-Id", required = false) Long sessionUserId,
      @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
    requireXmlHttpRequest(requestedWith);
    return withRefreshCookie(
        ResponseEntity.ok(), authService.refresh(refreshToken, sessionUserId), refreshTokenCookies);
  }

  @PostMapping("/auth/logout")
  @PermitAll
  public ResponseEntity<Void> logout(
      @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
      @RequestHeader(name = "Authorization", required = false) String authHeader,
      @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
    requireXmlHttpRequest(requestedWith);
    ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
    if (authSessionService.logout(authHeader, refreshToken)) {
      response.header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString());
    }
    return response.build();
  }

  /** 拒否されたリフレッシュトークンはもう使えないので cookie を消し、原因をヘッダで区別して返す。 */
  @ExceptionHandler(RefreshTokenRejectedException.class)
  public ResponseEntity<Map<String, Object>> handle(RefreshTokenRejectedException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(AUTH_FAILURE_HEADER, ex.getReason().headerValue())
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
        .body(Map.of("error", ex.getReason().message()));
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<MeResponse> me(Principal principal) {
    // 行が引けない＝無効なのはセッション（トークンの指す主体が存在しない）。404 にすると
    // 前端の全域 401 経路（再ログインへの差し戻し）に乗らないため、updateMe と同じ例外へ揃える。
    return authService
        .me(principal.getName())
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
  }

  @PutMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<MeResponse> updateMe(
      Principal principal, @Valid @RequestBody MeUpdateRequest req) {
    return ResponseEntity.ok(authService.updateMe(principal.getName(), req.getDisplayName()));
  }

  /** パスワード変更。版の増分により全端末のセッションが失効するため、クライアントは再ログインが必要。 */
  @PutMapping("/me/password")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> changePassword(
      Principal principal, @Valid @RequestBody PasswordChangeRequest request) {
    authService.changePassword(
        principal.getName(), request.getCurrentPassword(), request.getNewPassword());
    return ResponseEntity.noContent().build();
  }

  static ResponseEntity<Token> withRefreshCookie(
      ResponseEntity.BodyBuilder response, IssuedSession session, RefreshTokenCookies cookies) {
    return response
        .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refresh()).toString())
        .body(session.toToken());
  }

  /**
   * cookie を読む端点の CSRF 対策。別オリジンのページはこのヘッダを付けるとプリフライトが要り、CORS を開いていないので送れない。
   * {@code @RequestHeader(required = true)} は欠落を 400 にするため使わない。
   */
  private static void requireXmlHttpRequest(String requestedWith) {
    if (!"XMLHttpRequest".equals(requestedWith)) {
      throw new AccessDeniedException("X-Requested-With がありません");
    }
  }
}
