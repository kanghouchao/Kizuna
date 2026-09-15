package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 平台横断受注一覧の読み側 projection（集合作用域）。 濾過は storeSetFilter が Hibernate session 層で行うため、クエリ側に店舗の where
 * 句は持たない。
 */
public interface PlatformOrderView {
  OffsetDateTime getStartedAt();

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
