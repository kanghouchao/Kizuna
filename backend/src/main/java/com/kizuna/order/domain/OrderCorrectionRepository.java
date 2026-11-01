package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderCorrectionRepository extends StoreScopedRepository<OrderCorrection> {
  Optional<OrderCorrection> findFirstByStoreIdAndOrderIdOrderByAfterVersionDescIdDesc(
      Long storeId, String orderId);

  Page<OrderCorrection> findByStoreIdAndOrderId(Long storeId, String orderId, Pageable pageable);
}
