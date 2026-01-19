package com.kizuna.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

  @NotBlank(message = "username is required")
  private String username;

  @NotBlank(message = "password is required")
  private String password;
}
