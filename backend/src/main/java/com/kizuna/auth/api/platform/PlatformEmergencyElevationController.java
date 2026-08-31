package com.kizuna.auth.api.platform;

import com.kizuna.auth.api.dto.EmergencyElevationActivationResponse;
import com.kizuna.auth.api.dto.EmergencyElevationRequest;
import com.kizuna.auth.api.dto.EmergencyElevationSummaryResponse;
import com.kizuna.auth.application.EmergencyElevationService;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 緊急昇格の発動・履歴・撤回 API。いずれも EMERGENCY_ELEVATE 権限限定。 */
@RestController
@RequestMapping("/platform/emergency-elevations")
@RequiredArgsConstructor
public class PlatformEmergencyElevationController {

  private final EmergencyElevationService emergencyElevationService;

  /** 発動履歴（新しい発動から）。続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_EMERGENCY_ELEVATE')")
  public ResponseEntity<CursorPage<EmergencyElevationSummaryResponse>> list(
      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(emergencyElevationService.list(cursor, size));
  }

  /** 発動。新たな資源（昇格トークン）を生むので 201。トークンが現れるのはこの応答だけである。 */
  @PostMapping
  @PreAuthorize("hasAuthority('PERM_EMERGENCY_ELEVATE')")
  public ResponseEntity<EmergencyElevationActivationResponse> activate(
      Principal principal, @Valid @RequestBody EmergencyElevationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            emergencyElevationService.activate(
                principal.getName(),
                request.getStoreId(),
                request.getReason(),
                request.getPassword()));
  }

  /** 撤回。記録は追記型で消えないため DELETE ではなく名詞化した子リソースの生成で表す。 */
  @PostMapping("/{id}/revocation")
  @PreAuthorize("hasAuthority('PERM_EMERGENCY_ELEVATE')")
  public ResponseEntity<Void> revoke(@PathVariable Long id, Principal principal) {
    emergencyElevationService.revoke(id, principal.getName());
    return ResponseEntity.noContent().build();
  }
}
