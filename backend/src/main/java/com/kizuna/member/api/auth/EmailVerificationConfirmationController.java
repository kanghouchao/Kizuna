package com.kizuna.member.api.auth;

import com.kizuna.member.api.dto.EmailVerificationConfirmationRequest;
import com.kizuna.member.application.EmailVerificationConfirmation;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 検証メールのリンクからメールアドレスを確認する公開コントローラ。ログイン不要で、別端末からでも完了できる。 */
@RestController
@RequestMapping("/auth/email-verification")
@RequiredArgsConstructor
public class EmailVerificationConfirmationController {

  private final EmailVerificationConfirmation confirmation;

  @PostMapping("/confirmation")
  @PermitAll
  public ResponseEntity<Void> confirm(
      @Valid @RequestBody EmailVerificationConfirmationRequest request) {
    confirmation.confirm(request.getToken());
    return ResponseEntity.noContent().build();
  }
}
