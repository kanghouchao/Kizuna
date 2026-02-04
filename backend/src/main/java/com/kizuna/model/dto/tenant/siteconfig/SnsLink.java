package com.kizuna.model.dto.tenant.siteconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnsLink {
  private String platform;
  private String url;
  private String label;
}
