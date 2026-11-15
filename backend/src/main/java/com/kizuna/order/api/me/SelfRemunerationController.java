package com.kizuna.order.api.me;

import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.SelfRemunerationChangeResponse;
import com.kizuna.order.api.dto.SelfRemunerationEnrollmentSummary;
import com.kizuna.order.api.dto.SelfRemunerationResponse;
import com.kizuna.order.api.dto.SelfRemunerationSummary;
import com.kizuna.order.application.SelfRemunerationService;
import com.kizuna.shared.web.JwtClaims;
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
      @PageableDefault(
              size = 20,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service
        .enrollments(JwtClaims.userIdOf(principal), pageable)
        .map(mapper::toEnrollmentSummary);
  }

  @GetMapping("/remunerations")
  @PreAuthorize("hasRole('CAST')")
  public Page<SelfRemunerationSummary> list(
      Principal principal,
      @RequestParam(name = "enrollment_id", required = false) String enrollmentId,
      @PageableDefault(
              size = 20,
              sort = {"businessDate", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service
        .list(JwtClaims.userIdOf(principal), enrollmentId, pageable)
        .map(mapper::toRemunerationSummary);
  }

  @GetMapping("/remunerations/{orderId}")
  @PreAuthorize("hasRole('CAST')")
  public SelfRemunerationResponse detail(Principal principal, @PathVariable String orderId) {
    return mapper.toResponse(service.detail(JwtClaims.userIdOf(principal), orderId));
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
    return service
        .changes(JwtClaims.userIdOf(principal), orderId, pageable)
        .map(mapper::toResponse);
  }
}
