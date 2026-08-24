package com.kizuna.order.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCorrectionRepository extends JpaRepository<OrderCorrection, String> {

  /**
   * ある受注の訂正の鎖を古い順に辿る。ある訂正の「後値」＝次の訂正の「前値」または本体現値。
   *
   * <p>時刻に一意な副鍵（id）を重ねて全順序にする。鎖の意味は隣り合う 2 行の関係そのものなので、同じ時刻の 2 行で並びが揺れると鎖が繋がらない。
   */
  List<OrderCorrection> findByOrderIdOrderByCorrectedAtAscIdAsc(String orderId);
}
