package com.kizuna.notificationdelivery.domain;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface NotificationDeliveryRepository extends Repository<NotificationDelivery, String> {
  NotificationDelivery saveAndFlush(NotificationDelivery row);

  void flush();

  Optional<NotificationDelivery> findByAttributedStoreIdAndDedupeKey(Long storeId, String key);

  Page<NotificationDelivery> findByAttributedStoreId(Long storeId, Pageable pageable);

  Optional<NotificationDelivery> findByIdAndAttributedStoreId(String id, Long storeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from NotificationDelivery d where d.id=:id and d.attributedStoreId=:storeId")
  Optional<NotificationDelivery> findByIdAndAttributedStoreIdForUpdate(String id, Long storeId);

  /** 送信の定期処理（グループ全体）が引き渡した行を、その行の ID で取る。店舗を跨ぐのは意図どおり。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from NotificationDelivery d where d.id=:id")
  Optional<NotificationDelivery> findByIdForUpdate(String id);

  /** 送信の定期処理はグループ全体の処理で、全店舗とグループ級の到来分を扱う。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<NotificationDelivery> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
      DeliveryStatus status, OffsetDateTime time, Limit limit);
}
