package com.kizuna.user.api.store;

import com.kizuna.user.api.dto.StoreUserMeResponse;
import com.kizuna.user.api.dto.StoreUserProfileUpdateRequest;
import com.kizuna.user.application.StoreUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ログイン中の店舗ユーザー自身のアカウント情報。 */
@RestController
@RequestMapping("/tenant/me")
@RequiredArgsConstructor
public class StoreUserController {

  private final StoreUserService storeUserService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<StoreUserMeResponse> me(Principal principal) {
    return ResponseEntity.ok(storeUserService.me(principal.getName()));
  }

  @PutMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<StoreUserMeResponse> updateProfile(
      Principal principal, @Valid @RequestBody StoreUserProfileUpdateRequest request) {
    return ResponseEntity.ok(
        storeUserService.updateProfile(principal.getName(), request.getNickname()));
  }
}
