package com.kizuna.notificationdelivery.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DeliveryAttemptRepository extends StoreScopedRepository<DeliveryAttempt> {
  Page<DeliveryAttempt> findByStoreIdAndDeliveryId(
      Long storeId, String deliveryId, Pageable pageable);
}
