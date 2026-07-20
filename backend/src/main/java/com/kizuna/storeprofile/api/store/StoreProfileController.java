package com.kizuna.storeprofile.api.store;

import com.kizuna.storeprofile.api.dto.StoreProfileResponse;
import com.kizuna.storeprofile.api.dto.StoreProfileUpdateRequest;
import com.kizuna.storeprofile.application.StoreProfileService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/config")
@RequiredArgsConstructor
public class StoreProfileController {

  private final StoreProfileService storeProfileService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STORE_PROFILE_MANAGE')")
  public ResponseEntity<StoreProfileResponse> get() {
    return ResponseEntity.ok(storeProfileService.get());
  }

  @PutMapping
  @PreAuthorize("hasAuthority('PERM_STORE_PROFILE_MANAGE')")
  public ResponseEntity<StoreProfileResponse> update(
      @Valid @RequestBody StoreProfileUpdateRequest request) {
    return ResponseEntity.ok(storeProfileService.update(request));
  }

  @GetMapping("/public")
  @PermitAll
  public ResponseEntity<StoreProfileResponse> getPublic() {
    return ResponseEntity.ok(storeProfileService.get());
  }
}
