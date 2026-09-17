package com.kizuna.order.result;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 同じ訂正の再取得でも変わらない識別子と提供事実。金額は表示値で減項は種別で判別する。 */
public record OrderCorrectionResult(
    String correctionId,
    String orderId,
    Long storeId,
    LocalDate businessDate,
    OffsetDateTime completedAt,
    OffsetDateTime correctedAt,
    Long correctedBy,
    String reason,
    long beforeVersion,
    long afterVersion,
    Snapshot before,
    Snapshot after,
    String changeType) {
  public record Snapshot(
      LocalTime actualArrivalTime,
      LocalTime actualEndTime,
      Course course,
      List<FeeLine> feeLines,
      List<SpecialService> specialServices,
      int totalFee,
      int totalDurationMinutes,
      int totalRemuneration,
      int accruedRemuneration,
      boolean completionInvalidated) {
    public Snapshot {
      feeLines = List.copyOf(feeLines);
      specialServices = List.copyOf(specialServices);
    }
  }

  public record Course(
      String serviceId,
      String revisionId,
      long revisionNumber,
      String name,
      int durationMinutes,
      int price,
      int remuneration,
      String adoptionBasis,
      OffsetDateTime adoptedAt) {}

  public record FeeLine(
      String lineId,
      String kind,
      String name,
      int amount,
      Integer durationMinutes,
      int remuneration,
      boolean systemOwned,
      String serviceId,
      String revisionId,
      Long revisionNumber,
      String adoptionBasis,
      OffsetDateTime adoptedAt) {}

  public record SpecialService(
      String serviceId,
      String revisionId,
      long revisionNumber,
      long termsVersion,
      String name,
      String chargeType,
      int price,
      int remuneration,
      String adoptionBasis,
      OffsetDateTime adoptedAt,
      String enrollmentId,
      String consentEventId,
      Long consentVersion) {}
}
