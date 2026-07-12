package com.kizuna.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PlatformLoginRequest {

  @NotBlank(message = "email is required")
  @Email(message = "email format is invalid")
  private String email;

  @NotBlank(message = "password is required")
  private String password;
}
