package com.kizuna.controller.tenant;

import com.kizuna.model.dto.tenant.cast.CastCreateRequest;
import com.kizuna.model.dto.tenant.cast.CastResponse;
import com.kizuna.model.dto.tenant.cast.CastUpdateRequest;
import com.kizuna.service.tenant.CastService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  @GetMapping
  public ResponseEntity<Page<CastResponse>> list(
      @RequestParam(required = false) String search,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(castService.list(search, pageable));
  }

  @GetMapping("/{id}")
  public ResponseEntity<CastResponse> get(@PathVariable String id) {
    return ResponseEntity.ok(castService.get(id));
  }

  @PostMapping
  public ResponseEntity<CastResponse> create(@Valid @RequestBody CastCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(castService.create(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<CastResponse> update(
      @PathVariable String id, @Valid @RequestBody CastUpdateRequest request) {
    return ResponseEntity.ok(castService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    castService.delete(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/public")
  @PermitAll
  public ResponseEntity<List<CastResponse>> listPublic() {
    return ResponseEntity.ok(castService.listActive());
  }
}
