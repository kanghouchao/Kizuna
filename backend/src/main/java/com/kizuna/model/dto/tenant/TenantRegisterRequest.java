package com.kizuna.model.dto.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
@NoArgsConstructor
public class TenantRegisterRequest {
  @NotBlank
  @Length(min = 32)
  private String token;

  @NotBlank @Email private String email;

  @NotBlank
  @Min(value = 8, message = "パスワードは8文字以上でなければなりません")
  private String password;
}
