package com.kizuna.model.dto.tenant.tenantconfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class TenantConfigUpdateRequest {

  @Size(max = 50)
  private String templateKey;

  @Size(max = 500)
  @URL(message = "有効なロゴ URL を入力してください")
  private String logoUrl;

  @Size(max = 500)
  @URL(message = "有効なバナー URL を入力してください")
  private String bannerUrl;

  @Size(max = 500)
  @URL(message = "有効なメインビジュアル URL を入力してください")
  private String mvUrl;

  @Size(max = 20)
  private String mvType;

  private String description;

  @Valid private List<SnsLink> snsLinks;

  @Valid private List<PartnerLink> partnerLinks;
}
