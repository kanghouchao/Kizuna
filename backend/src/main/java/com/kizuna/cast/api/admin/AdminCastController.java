package com.kizuna.cast.api.admin;

import com.kizuna.cast.api.dto.AdminCastEnrollmentResponse;
import com.kizuna.cast.api.dto.AdminCastResponse;
import com.kizuna.cast.api.dto.AdminCastSummaryResponse;
import com.kizuna.cast.application.AdminCastService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/casts")
@RequiredArgsConstructor
public class AdminCastController {
  private final AdminCastService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CAST_PERSON_VIEW')")
  public Page<AdminCastSummaryResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(search, page, size);
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
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.enrollments(id, page, size);
  }
}
