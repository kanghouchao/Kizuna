package com.kizuna.model.dto.tenant.siteconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerLink {
  private String name;
  private String url;
  private String logoUrl;
}
