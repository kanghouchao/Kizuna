package com.kizuna.order.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderSpecialServiceEventRepository
    extends JpaRepository<OrderSpecialServiceEvent, String> {
  @Query(
      "select e from OrderSpecialServiceEvent e where e.orderId = :id and e.kind = 'REJECTED'"
          + " and not exists (select r.id from OrderSpecialServiceEvent r where r.rejectionEventId = e.id)")
  List<OrderSpecialServiceEvent> unresolved(String id);

  @Query(
      "select e from OrderSpecialServiceEvent e where e.orderId = :orderId"
          + " and (e.occurredAt < :at or (e.occurredAt = :at and e.id < :id)) order by e.occurredAt desc, e.id desc")
  List<OrderSpecialServiceEvent> history(
      String orderId, OffsetDateTime at, String id, Pageable pageable);
}
