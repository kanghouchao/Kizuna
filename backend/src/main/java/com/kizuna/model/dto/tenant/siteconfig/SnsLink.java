package com.kizuna.model.dto.tenant.siteconfig;

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
public class SnsLink {
  @NotBlank(message = "プラットフォームは必須です")
  private String platform;

  @NotBlank(message = "URL は必須です")
  @URL(message = "有効な URL を入力してください")
  private String url;

  private String label;
}
