package com.kizuna.user.api.dto;

import com.kizuna.shared.validation.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 最初の管理者の登録リクエスト。ロールと店舗集合はサーバーが決めるため受け取らない。
 *
 * <p>各項目の上限は従業員作成（{@link AdminStaffCreateRequest}）と同じで、根拠もそちらに書いてある。
 */
@Getter
@Setter
@NoArgsConstructor
public class InitialSetupRequest {

  @NotBlank(message = "email is required")
  @Email(message = "email format is invalid")
  @Size(max = 127)
  private String email;

  @NotBlank(message = "password is required")
  @Password
  private String password;

  @NotBlank(message = "display_name is required")
  @Size(max = 150)
  private String displayName;
}
