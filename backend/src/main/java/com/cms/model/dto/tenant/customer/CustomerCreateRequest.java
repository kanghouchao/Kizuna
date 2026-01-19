package com.cms.model.dto.tenant.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerCreateRequest {
  @NotBlank private String name;
  private String phoneNumber;
  private String phoneNumber2;
  private String address;
  private String buildingName;
  private String classification;
  private Boolean hasPet;
  private String ngType;
  private String ngContent;
}
