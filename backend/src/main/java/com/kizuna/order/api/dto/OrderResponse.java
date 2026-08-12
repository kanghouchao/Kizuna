package com.kizuna.order.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
  private String id;
  private Long receptionistId;
  private String receptionistName; // Helper for display
  private LocalDate businessDate;
  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;
  private String customerId;
  private String customerName; // Helper
  // 受付で録入された連絡先。顧客が着かなかった受注にだけ入る（着いた受注では台帳の行が名乗りを持つ）
  private String contactName;
  private String contactPhoneNumber;
  private String castId;
  private String castName; // Helper
  private Integer pax;
  private Integer courseMinutes;
  private Integer extensionMinutes;
  private List<String> optionCodes;
  private String discountName;
  private Integer manualDiscount;
  private String carrier;
  private String mediaName;
  private Integer totalFee;
  private Integer usedPoints;
  private Integer autoGrantPoints;
  private String remarks;
  private String castDriverMessage;
  private String status;
  private String receptionRoute;
  private String requesterMemberCode; // 申請した会員（店舗が起こした受注では null）
  private String locationAddress;
  private String locationBuilding;
}
