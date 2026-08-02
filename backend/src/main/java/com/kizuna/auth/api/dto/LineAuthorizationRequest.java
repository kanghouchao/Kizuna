package com.kizuna.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * LINE 認可コードの引き渡し（ログインと連携で共通）。JSON キーは Jackson 設定により snake_case（redirect_uri / code_verifier）。
 *
 * <p>{@code redirect_uri} は LINE の認可要求で使ったものと同一でなければならず、トークン交換時に LINE 自身が照合する。
 */
@Data
public class LineAuthorizationRequest {

  @NotBlank(message = "code is required")
  @Size(max = 512)
  private String code;

  @NotBlank(message = "redirect_uri is required")
  @Size(max = 512)
  private String redirectUri;

  @NotBlank(message = "code_verifier is required")
  @Size(max = 128)
  private String codeVerifier;
}
