package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 本人（キャスト）ポータル週間スケジュールの応答。JSON キーはグローバルの snake_case 設定に従う。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastScheduleResponse {
  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private String status;
  private Long storeId;
  private String storeName;
}
