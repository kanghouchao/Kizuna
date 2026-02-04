package com.kizuna.model.dto.tenant.siteconfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class SiteConfigUpdateRequest {

  @Size(max = 50)
  private String templateKey;

  @Size(max = 500)
  private String logoUrl;

  @Size(max = 500)
  private String bannerUrl;

  @Size(max = 500)
  private String mvUrl;

  @Size(max = 20)
  private String mvType;

  private String description;

  @Valid private List<SnsLink> snsLinks;

  @Valid private List<PartnerLink> partnerLinks;
}
