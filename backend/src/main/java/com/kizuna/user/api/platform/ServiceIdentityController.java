package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.ServiceIdentityCreateRequest;
import com.kizuna.user.api.dto.ServiceIdentityResponse;
import com.kizuna.user.api.dto.ServiceIdentityUpdateRequest;
import com.kizuna.user.application.ServiceIdentityService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * サービスID管理 API（本人種別 SERVICE の作成・一覧・授権変更・停止・再開）。全操作 SERVICE_ID_MANAGE 権限限定。
 *
 * <p>作成が授権（ロール×店舗集合）を伴うため、授権を一切動かさないアカウント管理（STAFF_ACCOUNT_MANAGE 門）には
 * 混ぜない。対話ログインできず全店バッチにもなりうる主体を、人のアカウントと別の鍵で仕切る（ADR 0025）。
 */
@RestController
@RequestMapping("/platform/service-identities")
@RequiredArgsConstructor
public class ServiceIdentityController {

  private final ServiceIdentityService serviceIdentityService;

  // 副キー id は offset ページングの全順序化のため（表示名は重複しうる）。
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SERVICE_ID_MANAGE')")
  public ResponseEntity<Page<ServiceIdentityResponse>> list(
      @RequestParam(required = false) String search,
      @PageableDefault(sort = {"displayName", "id"}) Pageable pageable) {
    return ResponseEntity.ok(serviceIdentityService.list(search, pageable));
  }

  @GetMapping("/grantable-roles")
  @PreAuthorize("hasAuthority('PERM_SERVICE_ID_MANAGE')")
  public ResponseEntity<List<RoleSummaryResponse>> grantableRoles() {
    return ResponseEntity.ok(serviceIdentityService.grantableRoles());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SERVICE_ID_MANAGE')")
  public ResponseEntity<ServiceIdentityResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(serviceIdentityService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_SERVICE_ID_MANAGE')")
  public ResponseEntity<ServiceIdentityResponse> create(
      @Valid @RequestBody ServiceIdentityCreateRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(serviceIdentityService.create(req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SERVICE_ID_MANAGE')")
  public ResponseEntity<ServiceIdentityResponse> update(
      @PathVariable Long id, @Valid @RequestBody ServiceIdentityUpdateRequest req) {
    return ResponseEntity.ok(serviceIdentityService.update(id, req));
  }

  @PostMapping("/{id}/suspension")
  @PreAuthorize("hasAuthority('PERM_SERVICE_ID_MANAGE')")
  public ResponseEntity<Void> suspend(@PathVariable Long id) {
    serviceIdentityService.suspend(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/resumption")
  @PreAuthorize("hasAuthority('PERM_SERVICE_ID_MANAGE')")
  public ResponseEntity<Void> resume(@PathVariable Long id) {
    serviceIdentityService.resume(id);
    return ResponseEntity.noContent().build();
  }
}
