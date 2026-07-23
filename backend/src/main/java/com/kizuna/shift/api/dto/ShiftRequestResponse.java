package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 出勤希望提出の応答（本人ポータル）。JSON キーはグローバルの snake_case 設定に従う。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftRequestResponse {
  private String id;
  private Long storeId;
  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private String note;
  private String status;
  private OffsetDateTime createdAt;
}
