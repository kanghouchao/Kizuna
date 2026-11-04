package com.kizuna.member.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 検証メールのリンクのトークンを運ぶ確認リクエスト。トークンはリンクのフラグメントから画面が読み、本文で送る。 */
@Data
public class EmailVerificationConfirmationRequest {

  @NotBlank(message = "token is required")
  // 発行するトークンは 43 文字。それより長い値はどのトークンとも一致しない。
  @Size(max = 64)
  private String token;
}
