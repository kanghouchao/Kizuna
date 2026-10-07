package com.kizuna.audit.recording;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditReader {
  private final AuditEventRepository events;
  private final EntityManager entityManager;

  public CursorPage<AuditEventSummary> list(String cursor, int requestedSize, String action) {
    int size = CursorPage.clampSize(requestedSize);
    if (action != null && !action.matches("[A-Z][A-Z0-9_]{0,79}")) {
      throw new ServiceException("監査操作の指定が不正です");
    }
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createQuery(AuditEvent.class);
    var root = query.from(AuditEvent.class);
    var predicates = new ArrayList<Predicate>();
    if (action != null) predicates.add(cb.equal(root.get("action"), action));
    if (cursor != null) {
      var after = PageCursor.decode(cursor);
      predicates.add(
          cb.or(
              cb.lessThan(root.get("occurredAt"), after.timestampKey()),
              cb.and(
                  cb.equal(root.get("occurredAt"), after.timestampKey()),
                  cb.lessThan(root.get("id"), after.longId()))));
    }
    query
        .where(predicates.toArray(Predicate[]::new))
        .orderBy(cb.desc(root.get("occurredAt")), cb.desc(root.get("id")));
    var rows = entityManager.createQuery(query).setMaxResults(size + 1).getResultList();
    return CursorPage.of(
            rows,
            size,
            row -> new PageCursor(row.getOccurredAt().toString(), row.getId().toString()).encode())
        .map(AuditReader::summary);
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
