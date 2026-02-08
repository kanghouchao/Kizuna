package com.kizuna.controller.tenant;

import com.kizuna.model.dto.tenant.girl.GirlCreateRequest;
import com.kizuna.model.dto.tenant.girl.GirlResponse;
import com.kizuna.model.dto.tenant.girl.GirlUpdateRequest;
import com.kizuna.service.tenant.GirlService;
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
@RequestMapping("/tenant/girls")
@RequiredArgsConstructor
public class GirlController {

  private final GirlService girlService;

  @GetMapping
  public ResponseEntity<Page<GirlResponse>> list(
      @RequestParam(required = false) String search,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(girlService.list(search, pageable));
  }

  @GetMapping("/{id}")
  public ResponseEntity<GirlResponse> get(@PathVariable String id) {
    return ResponseEntity.ok(girlService.get(id));
  }

  @PostMapping
  public ResponseEntity<GirlResponse> create(@Valid @RequestBody GirlCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(girlService.create(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<GirlResponse> update(
      @PathVariable String id, @Valid @RequestBody GirlUpdateRequest request) {
    return ResponseEntity.ok(girlService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    girlService.delete(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/public")
  @PermitAll
  public ResponseEntity<List<GirlResponse>> listPublic() {
    return ResponseEntity.ok(girlService.listActive());
  }
}
