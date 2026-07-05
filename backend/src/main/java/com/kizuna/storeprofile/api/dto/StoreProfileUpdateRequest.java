package com.kizuna.storeprofile.api.dto;

import com.kizuna.storeprofile.domain.PartnerLink;
import com.kizuna.storeprofile.domain.SnsLink;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class StoreProfileUpdateRequest {

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

  private String catchCopy;

  @Size(max = 500)
  private String address;

  @Size(max = 50)
  private String phone;

  @Size(max = 500)
  private String businessHours;

  private String pricingDescription;

  private Map<String, String> customTexts;

  @Valid private List<SnsLink> snsLinks;

  @Valid private List<PartnerLink> partnerLinks;
}
