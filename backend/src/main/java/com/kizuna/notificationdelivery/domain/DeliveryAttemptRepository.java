package com.kizuna.notificationdelivery.domain;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, String> {
  List<DeliveryAttempt> findByDeliveryIdAndIdLessThanOrderByIdDesc(
      String id, String before, Limit limit);
}
