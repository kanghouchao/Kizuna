package com.kizuna.order.domain;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;

public interface BusinessContactHistoryRepository
    extends StoreScopedRepository<BusinessContactHistory> {
  Optional<BusinessContactHistory> findFirstByStoreIdAndOrderIdAndTypeOrderByIdDesc(
      Long storeId, String orderId, ContactType type);

  List<BusinessContactHistory> findByStoreIdAndOrderIdAndIdLessThanOrderByIdDesc(
      Long storeId, String orderId, String id, Limit limit);
}
