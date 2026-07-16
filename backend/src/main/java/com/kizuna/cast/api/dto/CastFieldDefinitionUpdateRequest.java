package com.kizuna.cast.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CastFieldDefinitionUpdateRequest {

  // null は「変更しない」を意味するため許容する（Bean Validation は @Pattern/@Size とも null を有効扱い）。
  // 非 null の場合のみ、作成時の @NotBlank と対を成す「空白のみ不可」を課す。
  @Pattern(regexp = ".*\\S.*", message = "ラベルは空白のみにできません")
  @Size(max = 100)
  private String label;

  private Integer displayOrder;

  private Boolean isPublic;
}
