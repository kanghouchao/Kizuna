package com.kizuna.point.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRollbackRepository extends JpaRepository<PointRollback, Long> {

  Optional<PointRollback> findByOrderId(String orderId);

  /** その受注が既に巻き戻されているか。事後申領の拒否も二度目の拒否もこの述語で決まる。 */
  boolean existsByOrderId(String orderId);
}
