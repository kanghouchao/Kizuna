package com.kizuna.order.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCorrectionRepository extends JpaRepository<OrderCorrection, String> {

  /** ある受注の訂正の鎖を古い順に辿る。ある訂正の「後値」＝次の訂正の「前値」または本体現値。 */
  List<OrderCorrection> findByOrderIdOrderByCorrectedAtAsc(String orderId);
}
