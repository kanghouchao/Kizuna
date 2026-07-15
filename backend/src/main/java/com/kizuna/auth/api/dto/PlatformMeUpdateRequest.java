package com.kizuna.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PUT /platform/me のリクエスト。JSON キーは Jackson 設定により snake_case（display_name）。 */
@Getter
@Setter
@NoArgsConstructor
public class PlatformMeUpdateRequest {

  @NotBlank private String displayName;
}
