package com.kizuna.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerSelectionRequest(
    @NotNull Mode mode, String customerId, @Valid NewCustomer newCustomer) {
  public enum Mode {
    EXISTING,
    NEW,
    NONE
  }

  public record NewCustomer(@NotBlank @Size(max = 255) String name) {}
}
