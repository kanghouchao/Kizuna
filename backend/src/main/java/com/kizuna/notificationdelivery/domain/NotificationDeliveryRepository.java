package com.kizuna.notificationdelivery.domain;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NotificationDeliveryRepository
    extends JpaRepository<NotificationDelivery, String> {
  Optional<NotificationDelivery> findByDedupeKey(String key);

  List<NotificationDelivery> findByIdLessThanOrderByIdDesc(String id, Limit limit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from NotificationDelivery d where d.id=:id")
  Optional<NotificationDelivery> lockById(String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<NotificationDelivery> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
      DeliveryStatus status, OffsetDateTime time, Limit limit);
}
