package com.kizuna.member.api.platform;

import com.kizuna.member.api.dto.MemberRegistrationRequest;
import com.kizuna.member.api.dto.MemberRegistrationResponse;
import com.kizuna.member.application.MemberRegistrationService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会員の自助登録の公開コントローラ。StoreIdInterceptor を通らない /platform 配下に置く。
 *
 * <p>匿名 POST のため SecurityConfig の CSRF 免除対象（Bearer なしのため Bearer 免除に該当しない）。
 */
@RestController
@RequestMapping("/platform/members")
@RequiredArgsConstructor
public class PlatformMemberRegistrationController {

  private final MemberRegistrationService registrationService;

  @PostMapping
  @PermitAll
  public ResponseEntity<MemberRegistrationResponse> register(
      @Valid @RequestBody MemberRegistrationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
  }
}
