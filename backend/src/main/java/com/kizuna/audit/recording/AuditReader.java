package com.kizuna.audit.recording;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditReader {
  private final AuditEventRepository events;

  public Page<AuditEventSummary> list(String action, Pageable pageable) {
    if (action != null && !action.matches("[A-Z][A-Z0-9_]{0,79}")) {
      throw new ServiceException("監査操作の指定が不正です");
    }
    Specification<AuditEvent> spec =
        action == null
            ? Specification.unrestricted()
            : (root, query, cb) -> cb.equal(root.get("action"), action);
    return events.findAll(spec, pageable).map(AuditReader::summary);
  }

  public AuditEventResponse get(Long id) {
    var event = events.findById(id).orElseThrow(() -> new NotFoundException("監査履歴が見つかりません"));
    return new AuditEventResponse(summary(event), event.getBeforeValues(), event.getAfterValues());
  }

  private static AuditEventSummary summary(AuditEvent event) {
    return new AuditEventSummary(
        event.getId(),
        event.getOccurredAt(),
        event.getActorId(),
        event.getActorType(),
        event.getActorName(),
        event.getStoreId(),
        event.getAction(),
        event.getResult(),
        event.getTargetType(),
        event.getTargetId(),
        event.getSourceType(),
        event.getSourceId());
  }
}
