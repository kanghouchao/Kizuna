package com.kizuna.notificationdelivery.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NotificationDeliveryRepository
    extends StoreScopedRepository<NotificationDelivery> {
  Optional<NotificationDelivery> findByStoreIdAndDedupeKey(Long storeId, String key);

  Page<NotificationDelivery> findByStoreId(Long storeId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from NotificationDelivery d where d.id=:id and d.storeId=:storeId")
  Optional<NotificationDelivery> findByIdAndStoreIdForUpdate(String id, Long storeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<NotificationDelivery>
      findByStoreIdAndStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
          Long storeId, DeliveryStatus status, OffsetDateTime time, Limit limit);
}
