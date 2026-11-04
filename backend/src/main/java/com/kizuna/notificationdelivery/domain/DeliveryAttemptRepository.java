package com.kizuna.notificationdelivery.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** 試行は通知の子で店舗を持たない。境界は通知の ID で、呼び出し側が通知の帰属を先に確かめる。 */
public interface DeliveryAttemptRepository extends Repository<DeliveryAttempt, String> {
  DeliveryAttempt saveAndFlush(DeliveryAttempt attempt);

  Optional<DeliveryAttempt> findById(String id);

  Page<DeliveryAttempt> findByDeliveryId(String deliveryId, Pageable pageable);
}
