package com.kizuna.order.api.dto;

import com.kizuna.point.application.PointLedgerService.PointRollbackHistory;
import java.time.OffsetDateTime;

/** 当該受注で一度だけ成立した巻き戻しの結果。請求額は実行時の快照。 */
public record OrderPointRollbackResponse(
    String id,
    String reason,
    Long actorUserId,
    OffsetDateTime createdAt,
    long cancelledPoints,
    long restoredPoints,
    int beforeTotalFee,
    int offsetAmount,
    int afterTotalFee) {
  public static OrderPointRollbackResponse from(PointRollbackHistory history) {
    return new OrderPointRollbackResponse(
        history.id(),
        history.reason(),
        history.actorUserId(),
        history.createdAt(),
        history.cancelledPoints(),
        history.restoredPoints(),
        history.beforeTotalFee(),
        Math.toIntExact(history.restoredPoints()),
        history.afterTotalFee());
  }
}
