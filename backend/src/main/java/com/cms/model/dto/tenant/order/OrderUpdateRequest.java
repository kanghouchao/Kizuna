package com.cms.model.dto.tenant.order;

import java.time.LocalTime;
import java.util.List;
import lombok.Data;

@Data
public class OrderUpdateRequest {
  private String storeName;
  private String receptionistId;
  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;
  private String girlId;
  private Integer courseMinutes;
  private Integer extensionMinutes;
  private List<String> optionCodes;
  private String discountName;
  private Integer manualDiscount;
  private Integer usedPoints;
  private Integer manualGrantPoints;
  private String remarks;
  private String girlDriverMessage;
  private String status;
}
