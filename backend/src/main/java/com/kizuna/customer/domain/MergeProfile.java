package com.kizuna.customer.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record MergeProfile(
    @Size(max = 255) String name,
    @Size(max = 500) String address,
    @Size(max = 255) String buildingName,
    @Size(max = 255) String landmark,
    @Size(max = 50) String classification,
    Boolean hasPet,
    @Size(max = 255) String usageAreas,
    @Size(max = 50) String ngType,
    String ngContent) {
  public static MergeProfile from(Customer c) {
    return new MergeProfile(
        c.getName(),
        c.getAddress(),
        c.getBuildingName(),
        c.getLandmark(),
        c.getClassification(),
        c.getHasPet(),
        c.getUsageAreas(),
        c.getNgType(),
        c.getNgContent());
  }
}
