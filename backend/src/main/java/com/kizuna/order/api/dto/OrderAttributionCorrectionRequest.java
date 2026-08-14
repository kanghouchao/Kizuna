package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 誤帰属で付いたポイントを、帰属先の会員から差し引く要求（ADR 0012）。
 *
 * <p>帰属記録を ID で名指すのは、宛先を受注の現況から導かないためである。無効化のあと正しい本人が申領を済ませていれば
 * 受注には有効な帰属記録が並んで存在するが、訂正すべき相手は名指された<b>無効化済みの記録</b>が持つ会員である。
 *
 * <p>ポイントは引く量を正で受ける。この口は与える方向を持たない — 授権が「その付与が誤りだった」ことにだけ由来するため。
 */
@Data
public class OrderAttributionCorrectionRequest {

  @NotNull(message = "訂正する帰属記録は必須です")
  private Long attributionId;

  @NotNull(message = "差し引くポイントは必須です")
  @Positive(message = "差し引くポイントは 1 以上で指定してください")
  private Integer points;

  @NotBlank(message = "訂正の理由は必須です")
  @Size(max = 500, message = "訂正の理由は500文字以内です")
  private String reason;

  /** クライアント生成の冪等キー。応答喪失後の再送を初回と同じ操作として識別する（ADR 0007）。サーバは形式を解釈しない不透明な文字列として扱う。 */
  @NotBlank(message = "冪等キーは必須です")
  @Size(max = 64, message = "冪等キーは64文字以内です")
  private String idempotencyKey;
}
