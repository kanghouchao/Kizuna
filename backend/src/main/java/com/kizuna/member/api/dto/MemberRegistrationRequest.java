package com.kizuna.member.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 会員の自助登録リクエスト。JSON キーは Jackson 設定により snake_case（display_name）。 */
@Data
public class MemberRegistrationRequest {

  @NotBlank(message = "email is required")
  @Email(message = "email format is invalid")
  @Size(max = 255)
  private String email;

  @NotBlank(message = "password is required")
  @Size(min = 8, max = 100)
  private String password;

  @NotBlank(message = "display_name is required")
  @Size(max = 150)
  private String displayName;
}
