package com.kizuna.customer.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface CustomerContactHistoryRepository
    extends StoreScopedRepository<CustomerContactHistory> {
  String HISTORY_FROM =
      """
       from com.kizuna.customer.domain.CustomerContactHistory h
      join com.kizuna.customer.domain.CustomerContact c on c.id = h.contactId
      where h.storeId = :storeId and c.storeId = h.storeId and c.customerId = :customerId
      """;

  @Query(value = "select h" + HISTORY_FROM, countQuery = "select count(h)" + HISTORY_FROM)
  Page<CustomerContactHistory> history(Long storeId, String customerId, Pageable pageable);
}
