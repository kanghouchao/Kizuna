package com.kizuna.memberwithdrawal.api.me;

import com.kizuna.memberwithdrawal.api.dto.MemberWithdrawalRequest;
import com.kizuna.memberwithdrawal.application.MemberWithdrawalService;
import com.kizuna.shared.web.JwtClaims;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人（会員）ポータルの退会 API。成功後は旧トークンが失効しているので、応答は本文を持たない 204 で返しクライアントがログアウトする。
 *
 * <p>対象は subject の電子メールではなく検証済みの利用者 ID で固定する。電子メールは退会で解放されて後継が引き継ぎうるため、
 * 検証から解決までの間に再登録が割り込むと別人を退会させる。
 */
@RestController
@RequestMapping("/me/member/withdrawal")
@RequiredArgsConstructor
public class MemberWithdrawalController {

  private final MemberWithdrawalService memberWithdrawalService;

  @PostMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<Void> withdraw(
      Principal principal, @Valid @RequestBody MemberWithdrawalRequest request) {
    memberWithdrawalService.withdrawSelf(JwtClaims.userIdOf(principal), request.getReason());
    return ResponseEntity.noContent().build();
  }
}
