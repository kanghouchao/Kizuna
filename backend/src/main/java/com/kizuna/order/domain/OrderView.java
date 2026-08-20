package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

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

  /** この受注に実際に適用されたコース名の写し。 */
  String getCourseName();

  Integer getCourseMinutes();

  Integer getExtensionMinutes();

  String getCarrier();

  String getMediaName();

  /** 明細の総和。ポイント利用の減算も含むため、客が現金で払った額にあたる。 */
  Integer getTotalFee();

  /** 会計で利用したポイント。明細のポイント利用行の総和を正値へ翻した導出値で、利用の無い受注では 0。 */
  Integer getUsedPoints();

  Integer getAutoGrantPoints();

  String getRemarks();

  String getCastDriverMessage();

  OrderStatus getStatus();

  ReceptionRoute getReceptionRoute();

  String getRequesterMemberCode();

  /**
   * 申請時に会員が店舗へ名乗った名前。店舗が起こした受注では null。
   *
   * <p>未確定の申請では、これが受注の唯一の名乗りになりうる — 当店に台帳行の無い会員の初回申請は顧客が着かず、
   * 受付で録入する連絡先も持たないため、これを出さないと作業キューで「お客様名なし」としか呼べない。確定すると 台帳行が起きてそちらが正本になる（{@code
   * OrderService#ensureCustomerForRequester} がこの名前を写す）。
   */
  String getRequesterDeclaredName();

  String getLocationAddress();

  String getLocationBuilding();

  /** 取消の理由。取消していない受注では null。 */
  String getCancelledReason();

  /** 取消を実行した操作者の表示名。操作者が削除された取消では欠落する（FK が SET NULL のため）。 */
  String getCancelledByName();

  OffsetDateTime getCancelledAt();

  /** 受付時刻。予約受付 inbox の並びの鍵であり、続きを指すカーソルもこの値から組む。 */
  OffsetDateTime getCreatedAt();
}
