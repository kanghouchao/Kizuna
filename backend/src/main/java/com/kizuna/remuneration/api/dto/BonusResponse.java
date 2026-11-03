package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.application.RemunerationViews.BonusView;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BonusResponse(
    String id,
    Long personId,
    LocalDate awardDate,
    long amount,
    long effectiveAmount,
    String reason,
    long version,
    OffsetDateTime cancelledAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public static BonusResponse of(BonusView v) {
    return new BonusResponse(
        v.id(),
        v.personId(),
        v.awardDate(),
        v.amount(),
        v.effectiveAmount(),
        v.reason(),
        v.version(),
        v.cancelledAt(),
        v.createdAt(),
        v.updatedAt());
  }
}
