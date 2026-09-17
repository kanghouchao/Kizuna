package com.kizuna.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderCompletionInvalidationRequest(
    @NotNull @Min(0) Long expectedVersion, @NotBlank @Size(max = 500) String reason) {
  public OrderCompletionInvalidationRequest {
    reason = reason == null ? null : reason.trim();
  }
}
