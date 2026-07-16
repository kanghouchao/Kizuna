package com.kizuna.cast.api.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastFieldDefinitionResponse {
  private String id;
  private String key;
  private String label;
  private Integer displayOrder;
  private Boolean isPublic;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
