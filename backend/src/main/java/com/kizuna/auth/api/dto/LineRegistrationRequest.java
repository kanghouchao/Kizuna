package com.kizuna.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * LINE 登録の確定要求。JSON キーは Jackson 設定により snake_case（registration_ticket / display_name）。
 *
 * <p>LINE ユーザー ID は要求に含めない（チケットの裏にある検証済みの値を使う）。メールアドレスはここで初めて収集する — 身分の一意キーは email のままで、LINE
 * のみで登録した会員もパスワードログイン以外の全機構を共有する。
 */
@Data
public class LineRegistrationRequest {

  @NotBlank(message = "registration_ticket is required")
  @Size(max = 128)
  private String registrationTicket;

  @NotBlank(message = "display_name is required")
  @Size(max = 150)
  private String displayName;

  @NotBlank(message = "email is required")
  @Email(message = "email format is invalid")
  // 永続化前の小文字化で最大2倍に伸長する文字(U+0130 等)があっても t_users.email VARCHAR(255) に収まる上限。
  @Size(max = 127)
  private String email;
}
