package com.kizuna.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 会員紐づけリクエスト。JSON キーは Jackson 設定により snake_case（member_code）。 */
@Data
public class CustomerMemberLinkRequest {

  @NotBlank(message = "会員コードは必須です")
  @Pattern(regexp = "\\d{12}", message = "会員コードは数字 12 桁で入力してください")
  private String memberCode;
}
