package com.kizuna.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 未確定の予約申請に対する店舗側の編集内容。
 *
 * <p>受け取った値がそのまま新しい内容になる（省略・null は「未設定にする」）。部分更新の {@link OrderUpdateRequest}
 * と違い、指名や受付担当を明示的に外せることがこの契約の目的であり、「省略＝変更しない」では外す手段が無くなる。
 *
 * <p>そのため項目は編集画面が扱う 4 つだけに絞る。ここに増やした項目は、送られてこなければ消える。
 */
@Data
public class ReservationRequestUpdateRequest {

  /** 受付担当。null は未設定（確定時に実行者から補われる余地を残す）。 */
  private Long receptionistId;

  /** 指名するキャスト。null は指名なし。 */
  private String castId;

  @NotNull(message = "人数は必須です")
  @Min(value = 1, message = "人数は 1 以上です")
  private Integer pax;

  @Size(max = 500, message = "備考は 500 文字以内です")
  private String remarks;
}
