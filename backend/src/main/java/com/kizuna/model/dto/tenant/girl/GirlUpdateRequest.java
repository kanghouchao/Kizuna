package com.kizuna.model.dto.tenant.girl;

import lombok.Data;

@Data
public class GirlUpdateRequest {
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
}
