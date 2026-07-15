package com.kizuna.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PUT /platform/me のリクエスト。JSON キーは Jackson 設定により snake_case（display_name）。 */
@Getter
@Setter
@NoArgsConstructor
public class PlatformMeUpdateRequest {

  @NotBlank
  @Size(max = 150)
  private String displayName;
}
