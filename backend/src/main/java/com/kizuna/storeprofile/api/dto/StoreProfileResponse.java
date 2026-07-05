package com.kizuna.storeprofile.api.dto;

import com.kizuna.storeprofile.domain.PartnerLink;
import com.kizuna.storeprofile.domain.SnsLink;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreProfileResponse {
  private Long id;
  private String templateKey;
  private String logoUrl;
  private String bannerUrl;
  private String mvUrl;
  private String mvType;
  private String description;
  private String catchCopy;
  private String address;
  private String phone;
  private String businessHours;
  private String pricingDescription;
  private Map<String, String> customTexts;
  private List<SnsLink> snsLinks;
  private List<PartnerLink> partnerLinks;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
