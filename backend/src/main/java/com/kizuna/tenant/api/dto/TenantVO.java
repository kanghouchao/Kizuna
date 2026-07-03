package com.kizuna.tenant.api.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantVO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String name;
  private String domain;
  private String email;
}
