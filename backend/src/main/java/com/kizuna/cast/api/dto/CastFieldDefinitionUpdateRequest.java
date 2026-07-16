package com.kizuna.cast.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CastFieldDefinitionUpdateRequest {

  @Size(max = 100)
  private String label;

  private Integer displayOrder;

  private Boolean isPublic;
}
