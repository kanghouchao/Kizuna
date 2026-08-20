package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/** 会員本人の予約申請一覧の読み側 projection。店舗名とキャスト名だけを内联し、店舗台帳の項目は一切持たない。 */
public interface MemberOrderApplicationView {

  String getId();

  Long getStoreId();

  String getStoreName();

  LocalDate getBusinessDate();

  LocalTime getArrivalScheduledStartTime();

  Integer getPax();

  String getCastName();

  OrderApplicationStatus getStatus();
}
