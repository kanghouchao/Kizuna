package com.kizuna.order.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderCorrectionRepository extends JpaRepository<OrderCorrection, String> {
  Optional<OrderCorrection> findFirstByOrderIdOrderByAfterVersionDescIdDesc(String orderId);

  @Query(
      "select c from OrderCorrection c where c.orderId = :orderId"
          + " and (c.afterVersion < :version or (c.afterVersion = :version and c.id < :id))"
          + " order by c.afterVersion desc, c.id desc")
  List<OrderCorrection> history(String orderId, long version, String id, Pageable pageable);
}
