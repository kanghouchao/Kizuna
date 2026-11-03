package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.application.RemunerationViews.GuaranteeView;
import com.kizuna.remuneration.domain.GuaranteeState;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record GuaranteeResponse(
    String id,
    Long personId,
    LocalDate effectiveFrom,
    GuaranteeState state,
    Long dailyAmount,
    String reason,
    OffsetDateTime cancelledAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public static GuaranteeResponse of(GuaranteeView v) {
    return new GuaranteeResponse(
        v.id(),
        v.personId(),
        v.effectiveFrom(),
        v.state(),
        v.dailyAmount(),
        v.reason(),
        v.cancelledAt(),
        v.createdAt(),
        v.updatedAt());
  }
}
