package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 注文一覧・詳細の読み側 projection。関連集約の表示名は JPQL join で取得する（読み側は集約を経由しない）。 */
public interface OrderView {

  String getId();

  String getStoreName();

  Long getReceptionistId();

  String getReceptionistName();

  LocalDate getBusinessDate();

  LocalTime getArrivalScheduledStartTime();

  LocalTime getArrivalScheduledEndTime();

  String getCustomerId();

  String getCustomerName();

  String getCastId();

  String getCastName();

  Integer getCourseMinutes();

  Integer getExtensionMinutes();

  List<String> getOptionCodes();

  String getDiscountName();

  Integer getManualDiscount();

  String getCarrier();

  String getMediaName();

  Integer getUsedPoints();

  Integer getManualGrantPoints();

  String getRemarks();

  String getCastDriverMessage();

  OrderStatus getStatus();

  String getLocationAddress();

  String getLocationBuilding();
}
