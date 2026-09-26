package com.kizuna.customer.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerUpdateRequest {
  private String name;
  private String address;
  private String buildingName;

  @Size(max = 255)
  private String landmark;

  private String classification;
  private Boolean hasPet;
  private String usageAreas;
  private String ngType;
  private String ngContent;
}
