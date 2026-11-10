package com.kizuna.notificationdelivery.domain;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
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

  // グループ級（帰属店舗 null）の行。店舗の行とは別の照会で引き、null 許容の単一条件にしない。
  Optional<NotificationDelivery> findByAttributedStoreIdIsNullAndDedupeKey(String key);

  Page<NotificationDelivery> findByAttributedStoreIdIsNull(Pageable pageable);

  Optional<NotificationDelivery> findByIdAndAttributedStoreIdIsNull(String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from NotificationDelivery d where d.id=:id and d.attributedStoreId is null")
  Optional<NotificationDelivery> findByIdAndAttributedStoreIdIsNullForUpdate(String id);

  /** 送信の定期処理が引き渡した行を、その行の ID で取る。店舗集合の再検証は送信側が行う。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from NotificationDelivery d where d.id=:id")
  Optional<NotificationDelivery> findByIdForUpdate(String id);

  /**
   * 送信の定期処理が取る到来分。境界は実行するサービス ID の店舗集合で、店舗を持たないグループ級の行は集合に関わらず取る。 全店舗を授権された主体は条件なし、集合が空なら店舗の行を取らない。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<NotificationDelivery> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
      DeliveryStatus status, OffsetDateTime time, Limit limit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select d from NotificationDelivery d where d.status = :status and d.scheduledAt <= :time and (d.attributedStoreId is null or d.attributedStoreId in :storeIds) order by d.scheduledAt, d.id")
  List<NotificationDelivery> findDueForStores(
      DeliveryStatus status, OffsetDateTime time, Collection<Long> storeIds, Limit limit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<NotificationDelivery>
      findByStatusAndScheduledAtLessThanEqualAndAttributedStoreIdIsNullOrderByScheduledAtAscIdAsc(
          DeliveryStatus status, OffsetDateTime time, Limit limit);
}
