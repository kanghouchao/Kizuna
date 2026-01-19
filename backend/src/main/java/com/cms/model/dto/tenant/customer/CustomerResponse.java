package com.cms.model.dto.tenant.customer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerResponse {
  private String id;
  private String name;
  private String phoneNumber;
  private String phoneNumber2;
  private String address;
  private String buildingName;
  private String classification;
  private Boolean hasPet;
  private Integer points;
  private String ngType;
  private String ngContent;
}
