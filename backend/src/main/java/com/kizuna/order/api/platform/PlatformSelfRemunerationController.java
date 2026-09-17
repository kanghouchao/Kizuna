package com.kizuna.order.api.platform;

import com.kizuna.order.api.dto.SelfRemunerationChangeResponse;
import com.kizuna.order.api.dto.SelfRemunerationEnrollmentSummary;
import com.kizuna.order.api.dto.SelfRemunerationResponse;
import com.kizuna.order.api.dto.SelfRemunerationSummary;
import com.kizuna.order.application.SelfRemunerationService;
import com.kizuna.shared.web.CursorPage;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/me")
@RequiredArgsConstructor
public class PlatformSelfRemunerationController {
  private final SelfRemunerationService service;

  @GetMapping("/remuneration-enrollments")
  @PreAuthorize("hasRole('CAST')")
  public Page<SelfRemunerationEnrollmentSummary> enrollments(
      Principal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.enrollments(principal.getName(), page, size);
  }

  @GetMapping("/remunerations")
  @PreAuthorize("hasRole('CAST')")
  public Page<SelfRemunerationSummary> list(
      Principal principal,
      @RequestParam(name = "enrollment_id", required = false) String enrollmentId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(principal.getName(), enrollmentId, page, size);
  }

  @GetMapping("/remunerations/{orderId}")
  @PreAuthorize("hasRole('CAST')")
  public SelfRemunerationResponse detail(Principal principal, @PathVariable String orderId) {
    return service.detail(principal.getName(), orderId);
  }

  @GetMapping("/remunerations/{orderId}/changes")
  @PreAuthorize("hasRole('CAST')")
  public CursorPage<SelfRemunerationChangeResponse> changes(
      Principal principal,
      @PathVariable String orderId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.changes(principal.getName(), orderId, cursor, size);
  }
}
