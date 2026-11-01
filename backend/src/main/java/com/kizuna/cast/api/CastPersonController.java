package com.kizuna.cast.api;

import com.kizuna.cast.api.dto.AdminCastEnrollmentResponse;
import com.kizuna.cast.api.dto.AdminCastResponse;
import com.kizuna.cast.api.dto.AdminCastSummaryResponse;
import com.kizuna.cast.application.AdminCastService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cast-persons")
@RequiredArgsConstructor
public class CastPersonController {
  private final AdminCastService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CAST_PERSON_VIEW')")
  public Page<AdminCastSummaryResponse> list(
      @RequestParam(required = false) String search,
      @PageableDefault(
              size = 20,
              sort = {"id"})
          Pageable pageable) {
    return service.list(search, pageable);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CAST_PERSON_VIEW')")
  public AdminCastResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @GetMapping("/{id}/enrollments")
  @PreAuthorize("hasAuthority('PERM_CAST_PERSON_VIEW')")
  public Page<AdminCastEnrollmentResponse> enrollments(
      @PathVariable Long id,
      @PageableDefault(
              size = 20,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.enrollments(id, pageable);
  }
}
