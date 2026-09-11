package com.kizuna.cast.api.platform;

import com.kizuna.cast.api.dto.PlatformCastEnrollmentResponse;
import com.kizuna.cast.api.dto.PlatformCastResponse;
import com.kizuna.cast.api.dto.PlatformCastSummaryResponse;
import com.kizuna.cast.application.PlatformCastService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/casts")
@RequiredArgsConstructor
public class PlatformCastController {
  private final PlatformCastService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CAST_PERSON_VIEW')")
  public Page<PlatformCastSummaryResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(search, page, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CAST_PERSON_VIEW')")
  public PlatformCastResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @GetMapping("/{id}/enrollments")
  @PreAuthorize("hasAuthority('PERM_CAST_PERSON_VIEW')")
  public Page<PlatformCastEnrollmentResponse> enrollments(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.enrollments(id, page, size);
  }
}
