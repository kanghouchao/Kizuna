package com.kizuna.store.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StoreUpdateDTO {

  @NotBlank(message = "name is required")
  private String name;

  /**
   * 代表連絡先。PUT は全項目の置換であり、欠落は既存値を null で潰すことになるため必須で受ける。
   *
   * <p>長さ上限は列定義（VARCHAR(128)）に合わせる。超過を通すと保存時の制約違反が 500 になる。
   */
  @NotBlank(message = "email is required")
  @Size(max = 128, message = "email is too long")
  private String email;
}
