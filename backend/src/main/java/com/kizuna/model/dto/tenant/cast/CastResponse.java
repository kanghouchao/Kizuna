package com.kizuna.model.dto.tenant.cast;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CastResponse {
  private String id;
  private String name;
  private String status;
  private String photoUrl;
  private String introduction;
  private Integer age;
  private Integer height;
  private Integer bust;
  private Integer waist;
  private Integer hip;
  private Integer displayOrder;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
