package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.List;
import org.springframework.data.domain.Page;
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

  Page<OrderSpecialServiceEvent> findByStoreIdAndOrderId(
      Long storeId, String orderId, Pageable pageable);
}
