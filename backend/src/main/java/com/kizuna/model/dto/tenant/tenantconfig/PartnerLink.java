package com.kizuna.model.dto.tenant.tenantconfig;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerLink {
  @NotBlank(message = "名前は必須です")
  private String name;

  @NotBlank(message = "URL は必須です")
  @URL(message = "有効な URL を入力してください")
  private String url;

  @URL(message = "有効なロゴ URL を入力してください")
  private String logoUrl;
}
