package com.kizuna.model.dto.central.tenant;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TenantUpdateDTO {

  @NotBlank(message = "name is required")
  private String name;
}
