package com.cms.model.dto.tenant.girl;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GirlResponse {
  private String id;
  private String name;
  private String status;
}
