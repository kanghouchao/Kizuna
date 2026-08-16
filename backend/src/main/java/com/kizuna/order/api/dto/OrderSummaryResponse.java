package com.kizuna.order.api.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 顧客詳細の注文履歴 1 行。ある顧客の来店を日付順に辿るための最小限で、顧客は画面の文脈が既に持っているため載せない。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryResponse {
  private String id;
  private LocalDate businessDate;
  private String castName;
  private Integer courseMinutes;
  private Integer extensionMinutes;
  private Integer usedPoints;
  private String status;
}
