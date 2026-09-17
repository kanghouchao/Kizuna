package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 理由と確認済みの請求・相殺額。受注単位の一意性が再送を拒み、任意額は入力できない。 */
@Data
public class OrderPointRollbackRequest {

  @NotNull @PositiveOrZero private Integer expectedTotalFee;

  @NotNull @PositiveOrZero private Integer expectedOffsetAmount;

  @NotBlank(message = "巻き戻しの理由は必須です")
  @Size(max = 500, message = "巻き戻しの理由は 500 文字以内で入力してください")
  private String reason;

  public void setReason(String reason) {
    this.reason = reason == null ? null : reason.trim();
  }
}
