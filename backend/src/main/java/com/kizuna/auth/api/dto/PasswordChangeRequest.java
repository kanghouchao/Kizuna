package com.kizuna.auth.api.dto;

import com.kizuna.shared.validation.Password;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordChangeRequest {

  @NotBlank private String currentPassword;

  @NotBlank @Password private String newPassword;
}
