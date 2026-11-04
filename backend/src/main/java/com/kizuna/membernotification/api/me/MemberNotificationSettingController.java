package com.kizuna.membernotification.api.me;

import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.membernotification.api.dto.MemberNotificationSettingRequest;
import com.kizuna.membernotification.api.dto.MemberNotificationSettingsResponse;
import com.kizuna.membernotification.application.MemberNotificationSettingService;
import com.kizuna.shared.web.JwtClaims;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人（会員）ポータルの通知設定 API。対象はトークンの利用者 ID で固定する（電子メールは退会で後継へ移りうる）。 */
@RestController
@RequestMapping("/me/member/notification-settings")
@RequiredArgsConstructor
public class MemberNotificationSettingController {

  private final MemberNotificationSettingService service;

  @GetMapping
  @PreAuthorize("hasRole('MEMBER')")
  public MemberNotificationSettingsResponse view(Principal principal) {
    return MemberNotificationSettingsResponse.of(service.view(JwtClaims.userIdOf(principal)));
  }

  @PutMapping("/{purpose}")
  @PreAuthorize("hasRole('MEMBER')")
  public MemberNotificationSettingsResponse choose(
      Principal principal,
      @PathVariable ContactPurpose purpose,
      @Valid @RequestBody MemberNotificationSettingRequest request) {
    return MemberNotificationSettingsResponse.of(
        service.choose(JwtClaims.userIdOf(principal), purpose, request.status()));
  }
}
