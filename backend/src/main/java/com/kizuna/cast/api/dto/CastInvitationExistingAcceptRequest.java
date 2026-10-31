package com.kizuna.cast.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 既存の利用者による招待受諾リクエスト。資格情報を添えた一回の匿名要求で認証と受諾を済ませ、トークンも cookie も発行しない。
 *
 * <p>パスワードは既に設定済みの値を照合するだけなので、強度規則（{@code @Password}）は掛けない。
 */
@Data
public class CastInvitationExistingAcceptRequest {

  // トークンはパスではなく本文に載せる（理由は CastInvitationTokenRequest の javadoc）。
  @NotBlank(message = "token is required")
  // t_cast_invitations.token VARCHAR(64) と同じ上限。
  @Size(max = 64)
  private String token;

  @NotBlank(message = "email is required")
  @Email(message = "email format is invalid")
  private String email;

  @NotBlank(message = "password is required")
  private String password;
}
