package com.kizuna.customer.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CustomerCreateRequest {
  @NotBlank private String name;

  @Valid
  @Size(max = 100)
  @JsonSetter(nulls = Nulls.FAIL)
  private List<@NotNull ContactRequest> contacts = List.of();

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
