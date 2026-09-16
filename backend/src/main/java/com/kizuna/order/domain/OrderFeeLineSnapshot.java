package com.kizuna.order.domain;

import java.io.Serial;
import java.io.Serializable;

/** 訂正前後の採用条件。帯符号の金額と明細の同一性を保持する。 */
public record OrderFeeLineSnapshot(
    String lineId,
    OrderFeeLineKind kind,
    String name,
    String serviceId,
    Integer amount,
    Integer durationMinutes,
    int remuneration,
    OrderServiceAdoption adoption)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  static OrderFeeLineSnapshot of(OrderFeeLine line) {
    return new OrderFeeLineSnapshot(
        line.getId(),
        line.getKind(),
        line.getName(),
        line.getServiceId(),
        line.getAmount(),
        line.getDurationMinutes(),
        line.getRemuneration(),
        line.getAdoption());
  }
}
