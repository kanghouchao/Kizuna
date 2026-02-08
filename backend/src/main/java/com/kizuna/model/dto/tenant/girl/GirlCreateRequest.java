package com.kizuna.model.dto.tenant.girl;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GirlCreateRequest {
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
