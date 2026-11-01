package com.kizuna.service.api;

import com.kizuna.service.api.dto.ServiceCreateRequest;
import com.kizuna.service.api.dto.ServiceCreatedResponse;
import com.kizuna.service.api.dto.ServiceResponse;
import com.kizuna.service.api.dto.ServiceRevisionResponse;
import com.kizuna.service.api.dto.ServiceSummary;
import com.kizuna.service.api.dto.ServiceUpdateRequest;
import com.kizuna.service.application.ServiceSettingsService;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.shared.storescope.CurrentStore;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServiceSettingsController {
  private final ServiceSettingsService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public Page<ServiceSummary> list(
      @CurrentStore Long storeId,
      @RequestParam(required = false) ServiceKind kind,
      @RequestParam(defaultValue = "false") boolean deleted,
      // 作成直後のサービスを先頭ページに出す。ID は文字列で桁が増えると辞書順が作成順と
      // ずれるため作成日時で並べ、ID は同時刻の順序を固定する第 2 キーにとどめる。
      @PageableDefault(
              size = 20,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(storeId, kind, deleted, pageable);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public ResponseEntity<ServiceCreatedResponse> create(
      @CurrentStore Long storeId,
      @Valid @RequestBody ServiceCreateRequest request,
      Principal principal) {
    return ResponseEntity.status(201)
        .body(new ServiceCreatedResponse(service.create(storeId, request, principal.getName())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public ServiceResponse get(@CurrentStore Long storeId, @PathVariable String id) {
    return service.get(storeId, id);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public ServiceResponse update(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ServiceUpdateRequest request,
      Principal principal) {
    return service.update(storeId, id, request, principal.getName());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public ResponseEntity<Void> delete(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @RequestParam(name = "expected_version") long expectedVersion,
      Principal principal) {
    service.delete(storeId, id, expectedVersion, principal.getName());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/revisions")
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public Page<ServiceRevisionResponse> history(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @PageableDefault(
              size = 20,
              sort = {"revisionNumber", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.history(storeId, id, pageable);
  }
}
