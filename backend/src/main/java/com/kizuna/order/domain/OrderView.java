package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 注文一覧・詳細の読み側 projection。関連集約の表示名は JPQL join で取得する（読み側は集約を経由しない）。 */
public interface OrderView {

  String getId();

  Long getReceptionistId();

  String getReceptionistName();

  LocalDate getBusinessDate();

  LocalTime getArrivalScheduledStartTime();

  LocalTime getArrivalScheduledEndTime();

  String getCustomerId();

  String getCustomerName();

  /** 受付で録入された連絡先の氏名。顧客が着いた受注では空（名乗りの正本は台帳の側にある）。 */
  String getContactName();

  String getContactPhoneNumber();

  String getCastId();

  String getCastName();

  Integer getPax();

  Integer getCourseMinutes();

  Integer getExtensionMinutes();

  List<String> getOptionCodes();

  String getDiscountName();

  Integer getManualDiscount();

  String getCarrier();

  String getMediaName();

  Integer getTotalFee();

  Integer getUsedPoints();

  Integer getAutoGrantPoints();

  String getRemarks();

  String getCastDriverMessage();

  OrderStatus getStatus();

  ReceptionRoute getReceptionRoute();

  String getRequesterMemberCode();

  String getLocationAddress();

  String getLocationBuilding();

  /** 受付時刻。予約受付 inbox の並びの鍵であり、続きを指すカーソルもこの値から組む。 */
  OffsetDateTime getCreatedAt();
}
