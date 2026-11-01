package com.kizuna.order.api.me;

import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.SelfRemunerationChangeResponse;
import com.kizuna.order.api.dto.SelfRemunerationEnrollmentSummary;
import com.kizuna.order.api.dto.SelfRemunerationResponse;
import com.kizuna.order.api.dto.SelfRemunerationSummary;
import com.kizuna.order.application.SelfRemunerationService;
import java.security.Principal;
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
@RequestMapping("/me")
@RequiredArgsConstructor
public class SelfRemunerationController {
  private final SelfRemunerationService service;
  private final OrderMapper mapper;

  @GetMapping("/remuneration-enrollments")
  @PreAuthorize("hasRole('CAST')")
  public Page<SelfRemunerationEnrollmentSummary> enrollments(
      Principal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.enrollments(principal.getName(), page, size).map(mapper::toEnrollmentSummary);
  }

  @GetMapping("/remunerations")
  @PreAuthorize("hasRole('CAST')")
  public Page<SelfRemunerationSummary> list(
      Principal principal,
      @RequestParam(name = "enrollment_id", required = false) String enrollmentId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service
        .list(principal.getName(), enrollmentId, page, size)
        .map(mapper::toRemunerationSummary);
  }

  @GetMapping("/remunerations/{orderId}")
  @PreAuthorize("hasRole('CAST')")
  public SelfRemunerationResponse detail(Principal principal, @PathVariable String orderId) {
    return mapper.toResponse(service.detail(principal.getName(), orderId));
  }

  @GetMapping("/remunerations/{orderId}/changes")
  @PreAuthorize("hasRole('CAST')")
  public Page<SelfRemunerationChangeResponse> changes(
      Principal principal,
      @PathVariable String orderId,
      @PageableDefault(
              size = 20,
              sort = {"afterVersion", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.changes(principal.getName(), orderId, pageable).map(mapper::toResponse);
  }
}
