package com.kizuna.model.dto.tenant.cast;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CastCreateRequest {
  @NotBlank private String name;
  private String status;
  private String photoUrl;
  private String introduction;
  private Integer age;
  private Integer height;
  private Integer bust;
  private Integer waist;
  private Integer hip;
  private Integer displayOrder;
}
