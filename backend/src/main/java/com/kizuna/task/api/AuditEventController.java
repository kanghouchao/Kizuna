package com.kizuna.task.api;

import com.kizuna.audit.recording.AuditEventResponse;
import com.kizuna.audit.recording.AuditEventSummary;
import com.kizuna.audit.recording.AuditReader;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
  private final StoreScopeHolder storeScopes;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
  public Page<AuditEventSummary> list(
      @RequestParam(required = false) String action,
      @PageableDefault(
              size = 50,
              sort = {"occurredAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return reader.list(scope(), action, pageable);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_AUDIT_VIEW')")
  public AuditEventResponse get(@PathVariable Long id) {
    return reader.get(scope(), id);
  }

  private StoreScope scope() {
    var scope = storeScopes.current();
    if (scope == null) throw new AccessDeniedException("店舗集合スコープを解決できません");
    return scope;
  }
}
