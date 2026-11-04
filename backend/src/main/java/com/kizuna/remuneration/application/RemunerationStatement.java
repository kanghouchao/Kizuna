package com.kizuna.remuneration.application;

import com.kizuna.order.remuneration.OrderRemunerationFacts;
import com.kizuna.remuneration.domain.AcceptanceState;
import com.kizuna.remuneration.domain.DailyGuarantee;
import com.kizuna.remuneration.domain.GuaranteeState;
import com.kizuna.remuneration.domain.NotAcceptableReason;
import com.kizuna.remuneration.domain.StatementAcceptance;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public record RemunerationStatement(
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
    int acceptedDays,
    int acceptableDays,
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
      long bonusAmount,
      AcceptanceState acceptanceState,
      NotAcceptableReason notAcceptableReason,
      String contentDigest,
      AcceptanceSummary latestAcceptance) {}

  public record BonusItem(
      String id, LocalDate awardDate, long effectiveAmount, String reason, boolean cancelled) {}

  /** 承諾行のうち本人・店舗に返すもの。根拠の写しは載せない。 */
  public record AcceptanceSummary(
      String id,
      OffsetDateTime acceptedAt,
      long orderAmount,
      long guaranteeAmount,
      long bonusAmount) {
    public static AcceptanceSummary of(StatementAcceptance row) {
      return row == null
          ? null
          : new AcceptanceSummary(
              row.getId(),
              row.getAcceptedAt(),
              row.getOrderAmount(),
              row.getGuaranteeAmount(),
              row.getBonusAmount());
    }
  }
}
