package com.kizuna.task.api.platform;

import com.kizuna.audit.recording.AuditEventResponse;
import com.kizuna.audit.recording.AuditEventSummary;
import com.kizuna.audit.recording.AuditReader;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/audit-events")
@RequiredArgsConstructor
public class AuditEventController {
  private final AuditReader reader;
  private final ServiceExecutionIdentityService identities;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
  public CursorPage<AuditEventSummary> list(
      Authentication auth,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String action) {
    identities.requireOperator(auth.getName(), PermissionCode.AUDIT_VIEW);
    return reader.list(cursor, size, action);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
  public AuditEventResponse get(Authentication auth, @PathVariable Long id) {
    identities.requireOperator(auth.getName(), PermissionCode.AUDIT_VIEW);
    return reader.get(id);
  }
}
