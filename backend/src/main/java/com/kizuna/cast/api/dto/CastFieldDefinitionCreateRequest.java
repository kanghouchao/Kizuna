package com.kizuna.cast.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CastFieldDefinitionCreateRequest {

  @NotBlank
  @Pattern(
      regexp = "^(?!constructor$|prototype$)[a-z][a-z0-9_]*$",
      message = "キーは英小文字で始まり英小文字・数字・アンダースコアのみ使用でき、予約語(constructor・prototype)は使用できません")
  @Size(max = 50)
  private String key;

  @NotBlank
  @Size(max = 100)
  private String label;

  private Boolean isPublic;
}
