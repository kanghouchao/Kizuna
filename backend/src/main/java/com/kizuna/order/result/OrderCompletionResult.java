package com.kizuna.order.result;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderCompletionResult(
    String orderId,
    Long storeId,
    String castEnrollmentId,
    LocalDate businessDate,
    OffsetDateTime completedAt,
    List<Item> items,
    int accruedRemuneration,
    Long version,
    String latestCorrectionId,
    OrderCorrectionResult latestCorrection,
    boolean completionInvalidated,
    String replacementForOrderId) {
  public OrderCompletionResult {
    items = List.copyOf(items);
  }

  public record Item(
      String lineId,
      String kind,
      String name,
      int amount,
      Integer durationMinutes,
      int remuneration,
      String serviceId,
      String revisionId,
      Long revisionNumber,
      String adoptionBasis,
      OffsetDateTime adoptedAt,
      String enrollmentId,
      String consentEventId,
      Long consentVersion) {}
}
