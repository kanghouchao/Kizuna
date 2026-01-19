package com.cms.model.dto.tenant.order;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

@Data
public class OrderCreateRequest {
  private String storeName;
  private String receptionistId;

  @NotNull private LocalDate businessDate;

  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;

  private String customerId; // Usually look up or create customer first
  private String customerName; // Simple handling if no ID yet

  private String girlId;

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
  private String girlDriverMessage;
}
