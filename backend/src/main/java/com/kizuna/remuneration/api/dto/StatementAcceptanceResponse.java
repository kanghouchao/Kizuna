package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.application.RemunerationStatement.AcceptanceSummary;
import java.time.OffsetDateTime;

public record StatementAcceptanceResponse(
    String id,
    OffsetDateTime acceptedAt,
    long orderAmount,
    long guaranteeAmount,
    long bonusAmount) {
  public static StatementAcceptanceResponse of(AcceptanceSummary s) {
    return new StatementAcceptanceResponse(
        s.id(), s.acceptedAt(), s.orderAmount(), s.guaranteeAmount(), s.bonusAmount());
  }
}
