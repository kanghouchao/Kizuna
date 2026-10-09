package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.domain.GuaranteeState;
import com.kizuna.remuneration.domain.GuaranteeTerm;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
  public static GuaranteeResponse of(GuaranteeTerm t) {
    return new GuaranteeResponse(
        t.getId(),
        t.getPersonId(),
        t.getEffectiveFrom(),
        t.getState(),
        t.getDailyAmount(),
        t.getReason(),
        utc(t.getCancelledAt()),
        utc(t.getCreatedAt()),
        utc(t.getUpdatedAt()));
  }

  private static OffsetDateTime utc(OffsetDateTime value) {
    return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
