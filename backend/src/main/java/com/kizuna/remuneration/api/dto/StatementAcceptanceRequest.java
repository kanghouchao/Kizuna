package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.application.AcceptanceCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record StatementAcceptanceRequest(
    @NotNull Long storeId,
    @NotNull LocalDate businessDate,
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String contentDigest) {
  public AcceptanceCommand command() {
    return new AcceptanceCommand(storeId, businessDate, contentDigest);
  }
}
