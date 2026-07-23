package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 平台横断受注一覧の読み側 projection（集合作用域）。 濾過は storeSetFilter が Hibernate session 層で行うため、クエリ側に店舗の where
 * 句は持たない。
 */
public interface PlatformOrderView {

  String getId();

  Long getStoreId();

  String getStoreName();

  LocalDate getBusinessDate();

  LocalTime getArrivalScheduledStartTime();

  LocalTime getArrivalScheduledEndTime();

  OrderStatus getStatus();
}
