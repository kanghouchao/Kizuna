package com.kizuna.customer.api.dto;

import lombok.Data;

@Data
public class CustomerUpdateRequest {
  private String name;
  private String phoneNumber;
  private String phoneNumber2;
  private String address;
  private String buildingName;
  private String classification;
  private Boolean hasPet;
  private String rank;
  private String lineId;
  private String usageAreas;
  private String ngType;
  private String ngContent;
}
