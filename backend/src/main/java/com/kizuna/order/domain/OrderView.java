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

  /** 実際の到着時刻。完了後の訂正の門だけがこれを直せる。 */
  LocalTime getActualArrivalTime();

  /** 実際の終了時刻。同上。 */
  LocalTime getActualEndTime();

  String getCarrier();

  String getMediaName();

  /** 明細の総和。ポイント利用の減算も含むため、ポイント控除後の請求額にあたる。 */
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
   * 申請時に会員が店舗へ名乗った名前。店舗が起こした受注では null。 会員申請の確定時に顧客を新設する場合、{@code
   * CustomerProvisioningService#ensureMemberRequestCustomer} がこの名乗りを顧客へ写す。会員プロフィールから氏名を取得しない。
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

  /**
   * 楽観ロック用バージョン。全量置換の口（完了後訂正）が「画面が見ていた版」を名乗るために要る。
   *
   * <p>部分更新の口は触った項目だけを送るので版を要さない。全量を送る口は、送らなかった項目まで開いた 時点の値で押し戻すため、版が一致しない要求を撥ねる材料がここに要る。
   */
  Long getVersion();
}
