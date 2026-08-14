package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 確定済みの受注の取消。理由だけを受け取り、実行者と時刻はサーバが記録する。
 *
 * <p>理由は必須。既に受諾して確定シフトと客への約束を消費した後の取消であり、後から経緯を辿れる根拠はここにしか残らない（未確定申請の謝絶が理由を持たないのは、店舗がまだ受諾していない段階だから）。
 *
 * <p>これは<b>分類軸ではない</b>。「客都合 / 店都合 / 無断」のような集計可能な分類が要るようになったら列を足すのであって、情報の足りない今それを enum で固めない（ADR
 * 0013）。
 *
 * <p>上限は列長（500）と揃える。契約側で撥ねないと、超過が 400 ではなく DB のエラーになる。
 */
@Data
public class OrderCancellationRequest {

  @NotBlank(message = "取消の理由は必須です")
  @Size(max = 500, message = "取消の理由は 500 文字以内で入力してください")
  private String reason;
}
