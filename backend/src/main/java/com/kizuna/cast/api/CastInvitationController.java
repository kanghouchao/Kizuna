package com.kizuna.cast.api;

import com.kizuna.cast.api.dto.CastAcceptanceResponse;
import com.kizuna.cast.api.dto.CastInvitationAcceptRequest;
import com.kizuna.cast.api.dto.CastInvitationDetailResponse;
import com.kizuna.cast.api.dto.CastInvitationExistingAcceptRequest;
import com.kizuna.cast.api.dto.CastInvitationTokenRequest;
import com.kizuna.cast.application.CastInvitationAcceptanceService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 招待の閲覧と受諾を受け持つ公開コントローラ。 */
@RestController
@RequestMapping("/cast-invitations")
@RequiredArgsConstructor
public class CastInvitationController {

  private final CastInvitationAcceptanceService acceptanceService;
  private final AuthenticationManager authenticationManager;

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

  /**
   * 資格情報を照合してから受諾する。照合を受諾の取引の外に置くのは、取引内で利用者を読み込むと受諾の行ロックが版照合つきの昇格になり、
   * 新鮮な行を読み直せないため。照合から受諾までの間の停止とパスワード変更は、受諾側がロック後に拒む。
   */
  @PostMapping("/acceptance/existing")
  @PermitAll
  public ResponseEntity<CastAcceptanceResponse> acceptAsExistingUser(
      @Valid @RequestBody CastInvitationExistingAcceptRequest request) {
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                request.getEmail(), request.getPassword()));
    return ResponseEntity.ok(
        acceptanceService.acceptAsExistingUser(
            request.getToken(), authentication.getName(), request.getPassword()));
  }
}
