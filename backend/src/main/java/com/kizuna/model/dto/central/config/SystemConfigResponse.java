package com.kizuna.model.dto.central.config;

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
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
