package com.kizuna.remuneration.api.dto;

import com.kizuna.order.remuneration.OrderRemunerationFacts;
import com.kizuna.remuneration.domain.DailyGuarantee;
import com.kizuna.remuneration.domain.GuaranteeState;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public record RemunerationStatementResponse(
    Long storeId,
    String storeName,
    Long personId,
    String name,
    String month,
    OffsetDateTime generatedAt,
    long orderTotal,
    long knownGuaranteeTotal,
    Long guaranteeTotal,
    long bonusTotal,
    Long total,
    List<Day> days,
    Page<OrderRemunerationFacts.Item> orders,
    Page<BonusItem> bonusAwards) {
  public record Day(
      LocalDate businessDate,
      long orderAmount,
      GuaranteeState guaranteeState,
      Long dailyAmount,
      String closedDuration,
      boolean attendanceIncomplete,
      DailyGuarantee.Status guaranteeStatus,
      Long guaranteeAmount,
      long bonusAmount) {}

  public record BonusItem(
      String id, LocalDate awardDate, long effectiveAmount, String reason, boolean cancelled) {}
}
