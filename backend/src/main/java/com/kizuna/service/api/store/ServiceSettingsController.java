package com.kizuna.service.api.store;

import com.kizuna.service.api.dto.ServiceCreateRequest;
import com.kizuna.service.api.dto.ServiceCreatedResponse;
import com.kizuna.service.api.dto.ServiceResponse;
import com.kizuna.service.api.dto.ServiceRevisionResponse;
import com.kizuna.service.api.dto.ServiceSummary;
import com.kizuna.service.api.dto.ServiceUpdateRequest;
import com.kizuna.service.application.ServiceSettingsService;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
@RequestMapping("/store/services")
@RequiredArgsConstructor
public class ServiceSettingsController {
  private final ServiceSettingsService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public Page<ServiceSummary> list(
      @RequestParam(required = false) ServiceKind kind,
      @RequestParam(defaultValue = "false") boolean deleted,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(kind, deleted, page, size);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public ResponseEntity<ServiceCreatedResponse> create(
      @Valid @RequestBody ServiceCreateRequest request, Principal principal) {
    return ResponseEntity.status(201)
        .body(new ServiceCreatedResponse(service.create(request, principal.getName())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public ServiceResponse get(@PathVariable String id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public ServiceResponse update(
      @PathVariable String id,
      @Valid @RequestBody ServiceUpdateRequest request,
      Principal principal) {
    return service.update(id, request, principal.getName());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public ResponseEntity<Void> delete(
      @PathVariable String id,
      @RequestParam(name = "expected_version") long expectedVersion,
      Principal principal) {
    service.delete(id, expectedVersion, principal.getName());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/revisions")
  @PreAuthorize("hasAuthority('PERM_SERVICE_MANAGE')")
  public CursorPage<ServiceRevisionResponse> history(
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.history(id, cursor, size);
  }
}
