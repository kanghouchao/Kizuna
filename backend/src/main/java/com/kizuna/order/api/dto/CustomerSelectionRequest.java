package com.kizuna.order.api.dto;

import com.kizuna.shared.exception.ServiceException;
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

  public void validate() {
    if (mode == null
        || switch (mode) {
          case EXISTING -> customerId == null || customerId.isBlank() || newCustomer != null;
          case NEW ->
              customerId != null
                  || newCustomer == null
                  || newCustomer.name() == null
                  || newCustomer.name().isBlank();
          case NONE -> customerId != null || newCustomer != null;
        }) throw new ServiceException("既存顧客・新規顧客・顧客未設定のいずれかを指定してください");
  }
}
