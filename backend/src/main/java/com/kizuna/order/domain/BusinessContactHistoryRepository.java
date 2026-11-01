package com.kizuna.order.domain;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BusinessContactHistoryRepository
    extends StoreScopedRepository<BusinessContactHistory> {
  Optional<BusinessContactHistory> findFirstByStoreIdAndOrderIdAndTypeOrderByIdDesc(
      Long storeId, String orderId, ContactType type);

  Page<BusinessContactHistory> findByStoreIdAndOrderId(
      Long storeId, String orderId, Pageable pageable);
}
