package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 帰属記録の無効化。受け取るのは理由だけで、無効化の対象は受注 1 件に対して高々 1 件の有効な帰属記録に定まる。
 *
 * <p>理由は必須。無効化は帰属記録に対する唯一の訂正操作であり、後から「なぜ他人の来店が消えたのか」を辿れる根拠はここにしか残らない。
 *
 * <p>上限は列長（500）と揃える。契約側で撥ねないと、超過が 400 ではなく DB のエラーになる。
 */
@Data
public class OrderAttributionInvalidationRequest {

  @NotBlank(message = "無効化の理由は必須です")
  @Size(max = 500, message = "無効化の理由は 500 文字以内で入力してください")
  private String reason;
}
