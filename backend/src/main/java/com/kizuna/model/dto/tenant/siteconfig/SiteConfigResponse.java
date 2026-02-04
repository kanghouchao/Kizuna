package com.kizuna.model.dto.tenant.siteconfig;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteConfigResponse {
  private Long id;
  private String templateKey;
  private String logoUrl;
  private String bannerUrl;
  private String mvUrl;
  private String mvType;
  private String description;
  private List<SnsLink> snsLinks;
  private List<PartnerLink> partnerLinks;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
