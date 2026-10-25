package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface OrderCorrectionRepository extends StoreScopedRepository<OrderCorrection> {
  Optional<OrderCorrection> findFirstByStoreIdAndOrderIdOrderByAfterVersionDescIdDesc(
      Long storeId, String orderId);

  @Query(
      "select c from OrderCorrection c where c.storeId = :storeId and c.orderId = :orderId"
          + " and (c.afterVersion < :version or (c.afterVersion = :version and c.id < :id))"
          + " order by c.afterVersion desc, c.id desc")
  List<OrderCorrection> history(
      Long storeId, String orderId, long version, String id, Pageable pageable);
}
