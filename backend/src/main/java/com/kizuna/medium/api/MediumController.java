package com.kizuna.medium.api;

import com.kizuna.medium.api.dto.MediumRequests.CreateRequest;
import com.kizuna.medium.api.dto.MediumRequests.RenameRequest;
import com.kizuna.medium.api.dto.MediumRequests.VersionRequest;
import com.kizuna.medium.api.dto.MediumResponses.MediumCreatedResponse;
import com.kizuna.medium.api.dto.MediumResponses.MediumItemsResponse;
import com.kizuna.medium.api.dto.MediumResponses.MediumOptionResponse;
import com.kizuna.medium.api.dto.MediumResponses.MediumSummary;
import com.kizuna.medium.application.MediumService;
import com.kizuna.medium.domain.MediumStatus;
import com.kizuna.shared.storescope.CurrentStore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MediumController {
  private final MediumService service;

  @GetMapping("/media")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<MediumItemsResponse<MediumSummary>> list(
      @CurrentStore Long storeId, @RequestParam(required = false) MediumStatus status) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new MediumItemsResponse<>(service.list(storeId, status)));
  }

  // 受付・採用担当が広告費を見る権限なしに媒体を選べるよう、選ぶ側の権限のどれか一つで許す。
  @GetMapping("/media/options")
  @PreAuthorize(
      "hasAnyAuthority('PERM_ADVERTISING_COST_VIEW', 'PERM_ORDER_MANAGE', 'PERM_RECRUITMENT_MANAGE')")
  public ResponseEntity<MediumItemsResponse<MediumOptionResponse>> options(
      @CurrentStore Long storeId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new MediumItemsResponse<>(service.options(storeId)));
  }

  @PostMapping("/media")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public MediumCreatedResponse create(
      @CurrentStore Long storeId, @Valid @RequestBody CreateRequest request) {
    return new MediumCreatedResponse(service.create(storeId, request.name()));
  }

  @PutMapping("/media/{id}")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public MediumSummary rename(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody RenameRequest request) {
    return service.rename(storeId, id, request.name(), request.version());
  }

  @PostMapping("/media/{id}/stop")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public MediumSummary stop(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody VersionRequest request) {
    return service.stop(storeId, id, request.version());
  }

  @PostMapping("/media/{id}/resume")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public MediumSummary resume(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody VersionRequest request) {
    return service.resume(storeId, id, request.version());
  }

  @DeleteMapping("/media/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public void delete(
      @CurrentStore Long storeId, @PathVariable String id, @RequestParam long version) {
    service.delete(storeId, id, version);
  }
}
