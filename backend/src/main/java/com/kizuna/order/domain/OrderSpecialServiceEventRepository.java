package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface OrderSpecialServiceEventRepository
    extends StoreScopedRepository<OrderSpecialServiceEvent> {
  @Query(
      "select e from OrderSpecialServiceEvent e where e.storeId = :storeId and e.orderId = :id"
          + " and e.kind = 'REJECTED'"
          + " and not exists (select r.id from OrderSpecialServiceEvent r"
          + " where r.rejectionEventId = e.id and r.storeId = e.storeId)")
  List<OrderSpecialServiceEvent> unresolved(Long storeId, String id);

  @Query(
      "select e from OrderSpecialServiceEvent e where e.storeId = :storeId and e.orderId = :orderId"
          + " and (e.occurredAt < :at or (e.occurredAt = :at and e.id < :id)) order by e.occurredAt desc, e.id desc")
  List<OrderSpecialServiceEvent> history(
      Long storeId, String orderId, OffsetDateTime at, String id, Pageable pageable);
}
