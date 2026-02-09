package com.kizuna.model.dto.tenant.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

@Data
public class OrderCreateRequest {
  private String storeName;

  @NotBlank(message = "受付は必須です")
  private String receptionistId;

  @NotNull private LocalDate businessDate;

  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;

  private String customerId;
  private String customerName;
  private String phoneNumber;
  private String phoneNumber2;
  private String address;
  private String buildingName;
  private String classification;
  private String landmark;
  private Boolean hasPet;
  private String ngType;
  private String ngContent;

  @NotBlank(message = "キャストIDは必須です")
  private String castId;

  private Integer courseMinutes;
  private Integer extensionMinutes;
  private List<String> optionCodes;
  private String discountName;
  private Integer manualDiscount;
  private String carrier;
  private String mediaName;
  private Integer usedPoints;
  private Integer manualGrantPoints;
  private String remarks;
  private String castDriverMessage;
}
