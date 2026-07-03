package com.kizuna.auth.api.central;

import com.kizuna.auth.api.dto.AdminDto;
import com.kizuna.auth.api.dto.LoginRequest;
import com.kizuna.auth.api.dto.PasswordChangeRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.application.AuthSessionService;
import com.kizuna.auth.application.CentralAuthService;
import com.kizuna.user.domain.CentralUser;
import com.kizuna.user.domain.CentralUserRepository;
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

/**
 * Controller for Central Authentication.
 *
 * @author KangHouchao
 */
@RestController
@RequestMapping("/central")
@RequiredArgsConstructor
public class CentralAuthController {

  private final CentralAuthService authService;
  private final CentralUserRepository userRepository;
  private final AuthSessionService authSessionService;

  @PostMapping("/login")
  @PermitAll
  public ResponseEntity<Token> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req.getUsername(), req.getPassword()));
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<AdminDto> me(Principal principal) {
    if (principal == null || principal.getName() == null) {
      return ResponseEntity.status(401).build();
    }
    CentralUser user = userRepository.findByUsername(principal.getName()).orElse(null);
    if (user == null) return ResponseEntity.status(404).build();
    return ResponseEntity.ok(new AdminDto(user.getId(), user.getUsername(), user.getUsername()));
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

  @PostMapping("/logout")
  @PermitAll
  public ResponseEntity<?> logout(
      @RequestHeader(name = "Authorization", required = false) String authHeader) {
    authSessionService.invalidate(authHeader);
    return ResponseEntity.noContent().build();
  }
}
