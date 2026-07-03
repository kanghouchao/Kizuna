package com.kizuna.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StoreUserProfileUpdateRequest {

  @NotBlank
  @Size(max = 150)
  private String nickname;
}
