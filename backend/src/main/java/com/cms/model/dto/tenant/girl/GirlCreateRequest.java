package com.cms.model.dto.tenant.girl;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GirlCreateRequest {
  @NotBlank private String name;
  private String status;
}
