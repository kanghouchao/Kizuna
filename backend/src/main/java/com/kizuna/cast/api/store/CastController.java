package com.kizuna.cast.api.store;

import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastInvitationResponse;
import com.kizuna.cast.api.dto.CastPublicResponse;
import com.kizuna.cast.api.dto.CastResponse;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.application.CastInvitationService;
import com.kizuna.cast.application.CastService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant/casts")
@RequiredArgsConstructor
public class CastController {

  private final CastService castService;
  private final CastInvitationService castInvitationService;

  @GetMapping
  @PreAuthorize("hasAnyAuthority('ROLE_STORE_MANAGER','ROLE_STORE_STAFF')")
  public ResponseEntity<Page<CastResponse>> list(
      @RequestParam(required = false) String search,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(castService.list(search, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('ROLE_STORE_MANAGER','ROLE_STORE_STAFF')")
  public ResponseEntity<CastResponse> get(@PathVariable String id) {
    return ResponseEntity.ok(castService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('ROLE_STORE_MANAGER','ROLE_STORE_STAFF')")
  public ResponseEntity<CastResponse> create(@Valid @RequestBody CastCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(castService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('ROLE_STORE_MANAGER','ROLE_STORE_STAFF')")
  public ResponseEntity<CastResponse> update(
      @PathVariable String id, @Valid @RequestBody CastUpdateRequest request) {
    return ResponseEntity.ok(castService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('ROLE_STORE_MANAGER','ROLE_STORE_STAFF')")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    castService.delete(id);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{id}/invitation")
  @PreAuthorize("hasAuthority('ROLE_STORE_MANAGER')")
  public ResponseEntity<CastInvitationResponse> issueInvitation(@PathVariable String id) {
    return ResponseEntity.status(HttpStatus.CREATED).body(castInvitationService.issue(id));
  }

  @GetMapping("/public")
  @PermitAll
  public ResponseEntity<List<CastPublicResponse>> listPublic() {
    return ResponseEntity.ok(castService.listActive());
  }
}
