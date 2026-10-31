package com.kizuna.member.api;

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

/** 会員の自助登録の公開コントローラ。 */
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberRegistrationController {

  private final MemberRegistrationService registrationService;

  @PostMapping
  @PermitAll
  public ResponseEntity<MemberRegistrationResponse> register(
      @Valid @RequestBody MemberRegistrationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
  }
}
