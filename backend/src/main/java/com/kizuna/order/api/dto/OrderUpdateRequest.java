package com.kizuna.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

/**
 * 受注の部分更新の内容（null は「変更しない」）。確定後の受注のライフサイクルはこの契約が受け持つ。
 *
 * <p>状態は収めない。完了は完了処理が、取消は取消操作が、未確定申請の確定・謝絶は専用操作が独占するため、この口に残る合法な状態遷移は無い（ADR
 * 0013）。同じ理由で対象は確定済みの受注に限られ、 終端状態（完了・取消）の受注はこの口では書き換えられない。
 *
 * <p>会計金額・利用ポイント・自動付与ポイントも収めない — 完了処理だけが確定させる。受付経路も収めない（店舗側の合法値が PHONE のみで空回りするため）。
 *
 * <p>指名・受付担当に必須の宣言を置かないのは、どちらも未設定のまま正規の導線で確定しうるため — 会員は指名なしで申請でき、店舗は無効になった指名を確定前に外せる。受付担当も、確定した実行者が
 * 受付候補の条件を満たさなければ未設定のまま残る。契約の側で必須にすると、そうして生まれた受注は 人数を直すだけでも指名や受付担当を作り出さないと編集できない。
 *
 * <p>ただしこの 2 項目は、省略しても元の値が残る他の項目と違い、既に設定済みの受注では要求そのものが撥ねられる（{@link
 * com.kizuna.order.application.OrderService#update} が受注の状態を見て判定する）。省略が「変更しない」なのか「外す」なのかを
 * 契約の側で区別できないため、 指名・受付担当が外れた結果を黙って作らない。
 */
@Data
public class OrderUpdateRequest {
  private Long receptionistId;

  /** 営業日。改期はこの項目の変更で行う — 取消して再登録すると取消の記録が雑音で汚れる。 */
  private LocalDate businessDate;

  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;

  private String castId;

  @Min(value = 1, message = "人数は 1 以上です")
  private Integer pax;

  private Integer courseMinutes;
  private Integer extensionMinutes;
  private List<String> optionCodes;
  private String discountName;
  private Integer manualDiscount;

  /** 場所（住所）。誤記の訂正のために編集できる。 */
  private String locationAddress;

  /** 場所（建物名）。 */
  private String locationBuilding;

  /** 媒体（キャリア）。 */
  private String carrier;

  /** 媒体（知った媒体）。 */
  private String mediaName;

  private String remarks;
  private String castDriverMessage;

  /**
   * 受付で録入された連絡先の氏名。<b>顧客が着いていない受注の誤記の訂正のためだけ</b>にあり、顧客が着いた受注へ送ると 400 で撥ねられる（名乗りの正本は台帳の側にある）。
   *
   * <p>送っても台帳照合（0 件建档 / 1 件紐づけ / 複数断念）は再走しない。事後に受注を顧客へ着ける操作は別の口が担う。
   *
   * <p>上限は行き先の列と同じ（{@code t_orders.contact_name} = VARCHAR(255)）。契約で撥ねないと、溢れた値が更新時の SQLSTATE 22001
   * になり、理由の分かる 400 ではなく 500 で返る。
   */
  @Size(max = 255, message = "お客様名は 255 文字以内です")
  private String contactName;

  /**
   * 受付で録入された連絡先の電話番号。{@link #contactName} と同じ扱い（上限は {@code t_orders.contact_phone_number} =
   * VARCHAR(50)）。
   */
  @Size(max = 50, message = "電話番号は 50 文字以内です")
  private String contactPhoneNumber;
}
