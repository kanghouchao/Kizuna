package com.kizuna.notificationdelivery.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.List;
import org.springframework.data.domain.Limit;

public interface DeliveryAttemptRepository extends StoreScopedRepository<DeliveryAttempt> {
  List<DeliveryAttempt> findByStoreIdAndDeliveryIdAndIdLessThanOrderByIdDesc(
      Long storeId, String deliveryId, String before, Limit limit);
}
