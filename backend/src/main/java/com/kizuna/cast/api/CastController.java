package com.kizuna.cast.api;

import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastEnrollmentSnapshotResponse;
import com.kizuna.cast.api.dto.CastEnrollmentStatusHistoryResponse;
import com.kizuna.cast.api.dto.CastEnrollmentStatusResponse;
import com.kizuna.cast.api.dto.CastInvitationResponse;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastPublicResponse;
import com.kizuna.cast.api.dto.CastPublicationRequest;
import com.kizuna.cast.api.dto.CastPublicationResponse;
import com.kizuna.cast.api.dto.CastResponse;
import com.kizuna.cast.api.dto.CastSummaryResponse;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.application.CastEnrollmentService;
import com.kizuna.cast.application.CastInvitationService;
import com.kizuna.cast.application.CastService;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shared.web.CursorPage;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/casts")
@RequiredArgsConstructor
public class CastController {

  private final CastService castService;
  private final CastMapper castMapper;
  private final CastEnrollmentService enrollmentService;
  private final CastInvitationService castInvitationService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public ResponseEntity<Page<CastSummaryResponse>> list(
      @CurrentStore Long storeId,
      @RequestParam(required = false) String search,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(castService.list(storeId, search, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public ResponseEntity<CastResponse> get(@CurrentStore Long storeId, @PathVariable String id) {
    return ResponseEntity.ok(castService.get(storeId, id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public ResponseEntity<CastResponse> create(
      @CurrentStore Long storeId,
      @Valid @RequestBody CastCreateRequest request,
      Principal principal) {
    String id = castService.create(storeId, castMapper.toCommand(request), principal.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(castService.get(storeId, id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public ResponseEntity<CastResponse> update(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody CastUpdateRequest request,
      Principal principal) {
    return ResponseEntity.ok(castService.update(storeId, id, request, principal.getName()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public ResponseEntity<Void> delete(
      @CurrentStore Long storeId, @PathVariable String id, Principal principal) {
    castService.delete(storeId, id, principal.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/invitation")
  @PreAuthorize("hasAuthority('PERM_CAST_INVITE')")
  public ResponseEntity<CastInvitationResponse> issueInvitation(
      @CurrentStore Long storeId, @PathVariable String id, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(castInvitationService.issue(storeId, id, principal.getName()));
  }

  @PatchMapping("/{id}/publication")
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public ResponseEntity<CastPublicationResponse> changePublication(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody CastPublicationRequest request) {
    return ResponseEntity.ok(
        castService.changePublication(storeId, id, request.publicationStatus()));
  }

  @PostMapping("/{id}/suspension")
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public CastEnrollmentStatusResponse suspend(
      @CurrentStore Long storeId, @PathVariable String id, Principal principal) {
    return enrollmentService.suspend(storeId, id, principal.getName());
  }

  @PostMapping("/{id}/resumption")
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public CastEnrollmentStatusResponse resume(
      @CurrentStore Long storeId, @PathVariable String id, Principal principal) {
    return enrollmentService.resume(storeId, id, principal.getName());
  }

  @PostMapping("/{id}/withdrawal")
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public CastEnrollmentStatusResponse withdraw(
      @CurrentStore Long storeId, @PathVariable String id, Principal principal) {
    return enrollmentService.withdraw(storeId, id, principal.getName());
  }

  @GetMapping("/{id}/status-histories")
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public CursorPage<CastEnrollmentStatusHistoryResponse> history(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return enrollmentService.history(storeId, id, cursor, size);
  }

  @GetMapping("/{id}/snapshots")
  @PreAuthorize("hasAuthority('PERM_CAST_MANAGE')")
  public CursorPage<CastEnrollmentSnapshotResponse> snapshots(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return enrollmentService.snapshots(storeId, id, cursor, size);
  }

  @GetMapping("/public")
  @PermitAll
  public ResponseEntity<List<CastPublicResponse>> listPublic(@CurrentStore Long storeId) {
    return ResponseEntity.ok(castService.listPublished(storeId));
  }
}
