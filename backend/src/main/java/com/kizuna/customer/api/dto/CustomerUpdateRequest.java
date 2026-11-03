package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.CustomerPatch;
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

  /** null の項目は「変更しない」。 */
  public CustomerPatch patch() {
    return new CustomerPatch(
        name,
        address,
        buildingName,
        landmark,
        classification,
        hasPet,
        usageAreas,
        ngType,
        ngContent);
  }
}
