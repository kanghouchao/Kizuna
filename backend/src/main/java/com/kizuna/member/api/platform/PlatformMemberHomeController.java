package com.kizuna.member.api.platform;

import com.kizuna.member.api.dto.MemberHomeResponse;
import com.kizuna.member.application.MemberSelfService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人（会員）ポータルのホーム API。会員コードの提示（QR 表示）に使う。 */
@RestController
@RequestMapping("/platform/me/member")
@RequiredArgsConstructor
public class PlatformMemberHomeController {

  private final MemberSelfService memberSelfService;

  @GetMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<MemberHomeResponse> home(Principal principal) {
    return ResponseEntity.ok(memberSelfService.home(principal.getName()));
  }
}
