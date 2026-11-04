package com.kizuna.member.api.me;

import com.kizuna.member.application.MemberEmailVerificationService;
import com.kizuna.shared.web.JwtClaims;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人（会員）による検証メールの再送 API。匿名の再送口は置かない（宛先の列挙の面を増やさない）。
 *
 * <p>対象は subject の電子メールではなく検証済みの利用者 ID で固定する。電子メールは退会で解放されて後継が引き継ぎうる。
 */
@RestController
@RequestMapping("/me/email-verification")
@RequiredArgsConstructor
public class MemberEmailVerificationController {

  private final MemberEmailVerificationService memberEmailVerificationService;

  @PostMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<Void> resend(Principal principal) {
    memberEmailVerificationService.resend(JwtClaims.userIdOf(principal));
    return ResponseEntity.accepted().build();
  }
}
