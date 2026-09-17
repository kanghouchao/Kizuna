package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public interface SelfRemunerationView {
  String getOrderId();

  String getEnrollmentId();

  Long getStoreId();

  String getStoreName();

  LocalDate getBusinessDate();

  OffsetDateTime getCompletedAt();

  OrderStatus getStatus();

  boolean isCompletionInvalidated();

  int getAgreedRemuneration();

  Integer getAccruedRemuneration();
}
