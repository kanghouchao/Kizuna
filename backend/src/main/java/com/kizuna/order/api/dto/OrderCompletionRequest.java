package com.kizuna.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 受注完了（会計）の内容。ポイントの付与・利用はこの経路でのみ台帳へ入る。
 *
 * <p>会計金額は省略できない。付与の基準そのものであり、未指定を 0 として黙って通すと付与なしの完了が事故として成立する。
 *
 * <p>ポイント利用は任意だが、指定するなら 1 以上でなければならない。利用しない完了では項目ごと省略する — 0 を送ると撥ねられる。
 */
@Data
public class OrderCompletionRequest {

  @NotNull(message = "会計金額は必須です")
  @Min(value = 0, message = "会計金額は 0 以上です")
  private Integer totalFee;

  @Min(value = 1, message = "利用ポイントは 1 以上です")
  private Integer usePoints;
}
