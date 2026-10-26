package com.kizuna.memberwithdrawal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 本人による退会の要求。理由は必須（自由文）。 */
@Data
public class MemberWithdrawalRequest {

  @NotBlank(message = "退会理由を入力してください")
  @Size(max = 500, message = "退会理由は500文字以内で入力してください")
  private String reason;
}
