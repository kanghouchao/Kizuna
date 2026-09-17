package com.kizuna.order.domain;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.List;

/** 後続の訂正や設定変更から独立した、提供事実の不変な写し。 */
public record OrderCorrectionSnapshot(
    LocalTime actualArrivalTime,
    LocalTime actualEndTime,
    OrderCourse course,
    List<OrderFeeLineSnapshot> feeLines,
    List<SpecialServiceSnapshot> specialServices,
    int totalFee,
    int totalDurationMinutes,
    int totalRemuneration,
    int accruedRemuneration,
    boolean completionInvalidated)
    implements Serializable {
  public OrderCorrectionSnapshot {
    feeLines = List.copyOf(feeLines);
    specialServices = List.copyOf(specialServices);
  }

  public static OrderCorrectionSnapshot of(Order order) {
    return new OrderCorrectionSnapshot(
        order.getActualArrivalTime(),
        order.getActualEndTime(),
        order.getCourse(),
        order.getFeeLines().stream().map(OrderFeeLineSnapshot::of).toList(),
        order.getSpecialServices(),
        order.getTotalFee(),
        order.getTotalDurationMinutes(),
        order.getTotalRemuneration(),
        order.getAccruedRemuneration(),
        order.isCompletionInvalidated());
  }
}
