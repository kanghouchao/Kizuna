package com.kizuna.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/**
 * 予約申請の確定内容。確定は申請内容を予填した<b>受注の作成操作</b>で、店舗が補完・調整した値がそのまま受注になる（申請原文は動かない）。
 *
 * <p>ここに無い項目（割引・媒体・派遣先など）は、確定後の受注を汎用更新（{@code PUT /store/orders/{id}}）で整える — 確定は来店の約束に要る項目だけを決める。
 */
@Data
public class OrderApplicationConfirmationRequest {

  /** 受付担当。省略すると、実行者本人が受付候補の条件を満たす場合にだけ補われる（満たさなければ未設定のまま）。 */
  private Long receptionistId;

  @NotNull(message = "営業日は必須です")
  private LocalDate businessDate;

  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;

  /** 指名するキャスト。null は指名なし（会員は指名なしで申請できるため、確定でも強制しない）。 */
  private String castId;

  @Min(value = 1, message = "人数は 1 以上です")
  private Integer pax;

  private Integer courseMinutes;

  // 備考の行き先（t_orders.remarks）は TEXT のため上限を持たない
  private String remarks;
}
