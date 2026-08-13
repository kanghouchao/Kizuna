package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 帰属記録の無効化。対象の記録と、その訂正の理由を受け取る。
 *
 * <p>理由は必須。無効化は帰属記録に対する唯一の訂正操作であり、後から「なぜ他人の来店が消えたのか」を辿れる根拠はここにしか残らない。
 *
 * <p>上限は列長（500）と揃える。契約側で撥ねないと、超過が 400 ではなく DB のエラーになる。
 *
 * <p>対象の帰属記録は受注 ID から導かず、画面が見ていた記録を {@code attributionId} で名指す。導くと、画面を開いたまま別の操作者が 訂正一巡（無効化 → 再発行 →
 * 正しい本人の申領）を終えた場合に、古い理由が<b>新しく成立した正しい帰属</b>へ当たり、 取り戻したばかりの来店を消してしまう。
 */
@Data
public class OrderAttributionInvalidationRequest {

  @NotNull(message = "無効化する帰属記録の指定は必須です")
  private Long attributionId;

  @NotBlank(message = "無効化の理由は必須です")
  @Size(max = 500, message = "無効化の理由は 500 文字以内で入力してください")
  private String reason;
}
