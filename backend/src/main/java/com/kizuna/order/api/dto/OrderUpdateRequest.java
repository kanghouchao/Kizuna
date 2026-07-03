package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

@Data
public class OrderUpdateRequest {
  private String storeName;

  @NotBlank(message = "受付は必須です")
  private String receptionistId;

  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;

  @NotBlank(message = "キャストIDは必須です")
  private String castId;

  private Integer courseMinutes;
  private Integer extensionMinutes;
  private List<String> optionCodes;
  private String discountName;
  private Integer manualDiscount;
  private Integer usedPoints;
  private Integer manualGrantPoints;
  private String remarks;
  private String castDriverMessage;
  private String status;
}
