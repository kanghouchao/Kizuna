package com.kizuna.memberconsole.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 職員による代行退会の要求。理由は必須（自由文）。 */
@Data
public class StaffWithdrawalRequest {

  @NotBlank(message = "退会理由を入力してください")
  @Size(max = 500, message = "退会理由は500文字以内で入力してください")
  private String reason;
}
