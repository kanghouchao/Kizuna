package com.kizuna.audit.recording;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Subquery;
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
  private final EntityManager entityManager;

  public Page<AuditEventSummary> list(StoreScope scope, String action, Pageable pageable) {
    if (action != null && !action.matches("[A-Z][A-Z0-9_]{0,79}")) {
      throw new ServiceException("監査操作の指定が不正です");
    }
    Specification<AuditEvent> spec = visibleTo(scope);
    if (action != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
    return events.findAll(spec, pageable).map(AuditReader::summary);
  }

  public AuditEventResponse get(StoreScope scope, Long id) {
    Specification<AuditEvent> byId = (root, query, cb) -> cb.equal(root.get("id"), id);
    var event =
        events
            .findOne(visibleTo(scope).and(byId))
            .orElseThrow(() -> new NotFoundException("監査履歴が見つかりません"));
    return new AuditEventResponse(summary(event), event.getBeforeValues(), event.getAfterValues());
  }

  // 店舗は帰属属性で隔離軸ではないため、集合内の店舗に加えて店舗を持たない行と既に存在しない店舗の行を返す。集合が空なら後者の
  // 二つだけで、空の IN は組み立てない。店舗はエンティティ名で引き、store モジュールへの依存を作らない。
  private Specification<AuditEvent> visibleTo(StoreScope scope) {
    return (root, query, cb) -> {
      Path<Long> storeId = root.get("originatingStoreId");
      Subquery<Integer> storeExists = query.subquery(Integer.class);
      var store = storeExists.from(entityManager.getMetamodel().entity("Store"));
      storeExists.select(cb.literal(1)).where(cb.equal(store.get("id"), storeId));
      var visible = cb.or(cb.isNull(storeId), cb.not(cb.exists(storeExists)));
      return scope.storeIds().isEmpty() ? visible : cb.or(visible, storeId.in(scope.storeIds()));
    };
  }

  private static AuditEventSummary summary(AuditEvent event) {
    return new AuditEventSummary(
        event.getId(),
        event.getOccurredAt(),
        event.getActorId(),
        event.getActorType(),
        event.getActorName(),
        event.getOriginatingStoreId(),
        event.getAction(),
        event.getResult(),
        event.getTargetType(),
        event.getTargetId(),
        event.getSourceType(),
        event.getSourceId());
  }
}
