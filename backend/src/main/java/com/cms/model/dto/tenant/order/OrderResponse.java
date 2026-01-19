package com.cms.model.dto.tenant.order;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponse {
  private String id;
  private String storeName;
  private String receptionistId;
  private String receptionistName; // Helper for display
  private LocalDate businessDate;
  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;
  private String customerId;
  private String customerName; // Helper
  private String girlId;
  private String girlName; // Helper
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
  private String status;
}
