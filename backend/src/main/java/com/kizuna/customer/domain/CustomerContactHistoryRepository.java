package com.kizuna.customer.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerContactHistoryRepository
    extends JpaRepository<CustomerContactHistory, String> {
  String HISTORY =
      """
    select h from CustomerContactHistory h
    join CustomerContact c on c.id = h.contactId
    where c.customerId = :customerId
    """;

  @Query(HISTORY + " order by h.occurredAt desc, h.id desc")
  List<CustomerContactHistory> history(String customerId, Limit limit);

  @Query(
      HISTORY
          + " and (h.occurredAt < :time or (h.occurredAt = :time and h.id < :id)) order by h.occurredAt desc, h.id desc")
  List<CustomerContactHistory> historyAfter(
      String customerId, OffsetDateTime time, String id, Limit limit);
}
