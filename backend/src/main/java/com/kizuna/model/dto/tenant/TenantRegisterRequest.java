package com.kizuna.model.dto.tenant;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TenantRegisterRequest {
  @NotBlank private String token;

  @NotBlank private String email;

  @NotBlank private String password;
}
