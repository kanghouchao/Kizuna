package com.kizuna.settings.api.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigResponse {
  private Long id;
  private String configKey;
  private String configValue;
  private String category;
  private String description;
  private String valueType;
  private Boolean secret;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
