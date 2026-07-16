package com.kizuna.cast.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 招待の新規登録受諾リクエスト。JSON キーは Jackson 設定により snake_case（display_name）。 */
@Data
public class CastInvitationAcceptRequest {

  @NotBlank(message = "email is required")
  @Email(message = "email format is invalid")
  private String email;

  @NotBlank(message = "password is required")
  @Size(min = 8, max = 100)
  private String password;

  @NotBlank(message = "display_name is required")
  @Size(max = 150)
  private String displayName;
}
