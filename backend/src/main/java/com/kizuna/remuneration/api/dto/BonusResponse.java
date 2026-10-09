package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.domain.BonusAward;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
  public static BonusResponse of(BonusAward a) {
    return new BonusResponse(
        a.getId(),
        a.getPersonId(),
        a.getAwardDate(),
        a.getAmount(),
        a.effectiveAmount(),
        a.getReason(),
        a.getVersion(),
        utc(a.getCancelledAt()),
        utc(a.getCreatedAt()),
        utc(a.getUpdatedAt()));
  }

  private static OffsetDateTime utc(OffsetDateTime value) {
    return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
