package com.kizuna.cast.api.platform;

import com.kizuna.cast.api.dto.CastAcceptanceResponse;
import com.kizuna.cast.api.dto.CastInvitationAcceptRequest;
import com.kizuna.cast.api.dto.CastInvitationDetailResponse;
import com.kizuna.cast.api.dto.CastInvitationTokenRequest;
import com.kizuna.cast.application.CastInvitationAcceptanceService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 招待受諾の公開コントローラ。StoreIdInterceptor を通らない /platform 配下に置く。 */
@RestController
@RequestMapping("/platform/cast-invitations")
@RequiredArgsConstructor
public class PlatformCastInvitationController {

  private final CastInvitationAcceptanceService acceptanceService;

  @PostMapping("/view")
  @PermitAll
  public ResponseEntity<CastInvitationDetailResponse> view(
      @Valid @RequestBody CastInvitationTokenRequest request) {
    return ResponseEntity.ok(acceptanceService.view(request.getToken()));
  }

  @PostMapping("/acceptance")
  @PermitAll
  public ResponseEntity<CastAcceptanceResponse> acceptAsNewUser(
      @Valid @RequestBody CastInvitationAcceptRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(acceptanceService.acceptAsNewUser(request.getToken(), request));
  }

  @PostMapping("/acceptance/existing")
  @PreAuthorize("hasAuthority('ROLE_CAST')")
  public ResponseEntity<CastAcceptanceResponse> acceptAsExistingUser(
      @Valid @RequestBody CastInvitationTokenRequest request, Principal principal) {
    return ResponseEntity.ok(
        acceptanceService.acceptAsExistingUser(request.getToken(), principal.getName()));
  }
}
