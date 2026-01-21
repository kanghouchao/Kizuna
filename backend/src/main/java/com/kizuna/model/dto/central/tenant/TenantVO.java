package com.kizuna.model.dto.central.tenant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantVO {
  private String id;
  private String name;
  private String domain;
  private String email;
}
