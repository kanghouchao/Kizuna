package com.kizuna.order.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 平台横断受注一覧のレスポンスDTO。JSON キーはグローバルの snake_case 設定に従う。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformOrderResponse {
  private String id;
  private Long storeId;
  private String storeName;
  private LocalDate businessDate;
  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;
  private String status;
}
