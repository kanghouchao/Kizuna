package com.kizuna.task.api;

import com.kizuna.audit.recording.AuditEventResponse;
import com.kizuna.audit.recording.AuditEventSummary;
import com.kizuna.audit.recording.AuditReader;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-events")
@RequiredArgsConstructor
public class AuditEventController {
  private final AuditReader reader;
  private final ServiceExecutionIdentityService identities;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
  public Page<AuditEventSummary> list(
      Authentication auth,
      @RequestParam(required = false) String action,
      @PageableDefault(
              size = 50,
              sort = {"occurredAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    identities.requireOperator(auth.getName(), PermissionCode.AUDIT_VIEW);
    return reader.list(action, pageable);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
  public AuditEventResponse get(Authentication auth, @PathVariable Long id) {
    identities.requireOperator(auth.getName(), PermissionCode.AUDIT_VIEW);
    return reader.get(id);
  }
}
