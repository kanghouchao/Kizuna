package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** 全群横断受注一覧の読み側 projection（集合作用域）。店舗の絞り込みは授権店舗集合の形に応じてクエリが持つ。 */
public interface AdminOrderView {
  boolean getCompletionInvalidated();

  String getReplacementForOrderId();

  OffsetDateTime getStartedAt();

  OffsetDateTime getCompletedAt();

  int getAccruedRemuneration();

  int getTotalRemuneration();

  int getTotalFee();

  Integer getUnresolvedSpecialServiceCount();

  default boolean getRequiresAttention() {
    return getUnresolvedSpecialServiceCount() != null && getUnresolvedSpecialServiceCount() > 0;
  }

  OrderCourse getCourse();

  String getId();

  Long getStoreId();

  LocalDate getBusinessDate();

  LocalTime getArrivalScheduledStartTime();

  LocalTime getArrivalScheduledEndTime();

  OrderStatus getStatus();
}
