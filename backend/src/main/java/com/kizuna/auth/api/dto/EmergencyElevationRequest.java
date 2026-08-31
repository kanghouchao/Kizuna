package com.kizuna.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 緊急昇格の発動要求。パスワードを載せ、セッションの奪取だけでは越えられない一段を発動の直前に置く。 */
@Data
public class EmergencyElevationRequest {

  @NotNull(message = "昇格の対象店舗は必須です")
  private Long storeId;

  @NotBlank(message = "発動の理由は必須です")
  @Size(max = 500, message = "発動の理由は500文字以内です")
  private String reason;

  @NotBlank(message = "パスワードは必須です")
  private String password;
}
