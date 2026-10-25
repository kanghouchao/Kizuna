package com.kizuna.customer.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;

public interface CustomerContactHistoryRepository
    extends StoreScopedRepository<CustomerContactHistory> {
  String HISTORY =
      """
    select h from com.kizuna.customer.domain.CustomerContactHistory h
    join com.kizuna.customer.domain.CustomerContact c on c.id = h.contactId
    where h.storeId = :storeId and c.storeId = h.storeId and c.customerId = :customerId
    """;

  @Query(HISTORY + " order by h.occurredAt desc, h.id desc")
  List<CustomerContactHistory> history(Long storeId, String customerId, Limit limit);

  @Query(
      HISTORY
          + " and (h.occurredAt < :time or (h.occurredAt = :time and h.id < :id)) order by h.occurredAt desc, h.id desc")
  List<CustomerContactHistory> historyAfter(
      Long storeId, String customerId, OffsetDateTime time, String id, Limit limit);
}
