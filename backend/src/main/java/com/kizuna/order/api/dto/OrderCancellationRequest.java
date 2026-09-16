package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 未完了（CONFIRMED / IN_SERVICE）の受注の取消。理由だけを受け取り、実行者と時刻はサーバが記録する。
 * 理由は経緯を辿るための自由記述で、集計分類ではない。上限は列長と揃え、超過を入力エラーとして拒否する。
 */
@Data
public class OrderCancellationRequest {

  @NotBlank(message = "取消の理由は必須です")
  @Size(max = 500, message = "取消の理由は 500 文字以内で入力してください")
  private String reason;
}
