package com.kizuna.store.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;

@Getter
@Setter
@NoArgsConstructor
public class StoreUpdateDTO {

  @NotBlank(message = "name is required")
  private String name;

  /** 前後の空白（全角を含む）を除いた値を検証・保存する。字数は UTF-16 ではなくコードポイントで数え、全角・半角とも 1 文字。 */
  @NotBlank(message = "略称を入力してください")
  @CodePointLength(max = 3, message = "略称は 3 文字以内で入力してください")
  private String shortName;

  public void setShortName(String shortName) {
    this.shortName = ShortNameText.trim(shortName);
  }
}
