package com.kizuna.user.api.auth;

import com.kizuna.user.api.dto.InitialSetupCreationResponse;
import com.kizuna.user.api.dto.InitialSetupRequest;
import com.kizuna.user.api.dto.InitialSetupStatusResponse;
import com.kizuna.user.application.InitialSetupService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最初の管理者を登録する初期設定の公開端点。完了後の登録は 404。
 *
 * <p>POST は SecurityConfig の CSRF 免除と ExemptingBearerTokenResolver の Bearer 免除の対象。GET
 * は初期設定画面のサーバー側から Bearer なしで呼ばれるため、どちらにも登録しない。
 */
@RestController
@RequestMapping("/auth/initial-setup")
@RequiredArgsConstructor
public class InitialSetupController {

  private final InitialSetupService initialSetupService;

  @GetMapping
  @PermitAll
  public ResponseEntity<InitialSetupStatusResponse> status() {
    return ResponseEntity.ok(new InitialSetupStatusResponse(initialSetupService.required()));
  }

  @PostMapping
  @PermitAll
  public ResponseEntity<InitialSetupCreationResponse> register(
      @Valid @RequestBody InitialSetupRequest request) {
    Long id = initialSetupService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(new InitialSetupCreationResponse(id));
  }
}
