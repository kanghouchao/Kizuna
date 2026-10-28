package com.kizuna.order.application;

import com.kizuna.shared.exception.ServiceException;

/** 受注に着ける顧客の選び方。既存・新規・未設定のいずれか一つで、組合せの誤りは {@link #validate} が撥ねる。 */
public record CustomerSelection(Mode mode, String customerId, NewCustomer newCustomer) {

  public enum Mode {
    EXISTING,
    NEW,
    NONE
  }

  public record NewCustomer(String name) {}

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
