package com.kizuna.order.api.dto;

import jakarta.validation.constraints.Min;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

/**
 * 受注の部分更新の内容（null は「変更しない」）。確定後の受注のライフサイクルはこの契約が受け持つ。
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
  private String remarks;
  private String castDriverMessage;
  private String status;
}
