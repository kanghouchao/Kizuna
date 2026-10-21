package com.kizuna.auth.api.auth;

import com.kizuna.auth.api.dto.LoginRequest;
import com.kizuna.auth.api.dto.MeResponse;
import com.kizuna.auth.api.dto.MeUpdateRequest;
import com.kizuna.auth.api.dto.PasswordChangeRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.application.AuthService;
import com.kizuna.auth.application.AuthSessionService;
import com.kizuna.shared.exception.StaleSessionException;
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
import org.springframework.web.bind.annotation.RestController;

/** 統一認証のコントローラ。ログイン・ログアウトは /auth、本人資料は /me に掛ける。 */
@RestController
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final AuthSessionService authSessionService;

  @PostMapping("/auth/login")
  @PermitAll
  public ResponseEntity<Token> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req.getEmail(), req.getPassword()));
  }

  @PostMapping("/auth/logout")
  @PermitAll
  public ResponseEntity<Void> logout(
      @RequestHeader(name = "Authorization", required = false) String authHeader) {
    authSessionService.invalidate(authHeader);
    return ResponseEntity.noContent().build();
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
}
