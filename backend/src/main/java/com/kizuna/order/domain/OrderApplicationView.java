package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/** 店舗の予約受付箱（申請一覧）の読み側 projection。キャスト名だけを内联する。 */
public interface OrderApplicationView {

  String getId();

  LocalDate getBusinessDate();

  LocalTime getArrivalScheduledStartTime();

  Integer getPax();

  String getCastId();

  String getCastName();

  String getRemarks();

  OrderApplicationStatus getStatus();

  String getRequesterMemberCode();

  String getRequesterDeclaredName();

  String getContactName();

  String getContactPhoneNumber();

  String getOrderId();

  String getDeclinedReason();
}
