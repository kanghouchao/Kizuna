package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftResponse {
  private String id;
  private String castId;
  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private String status;

  /** 店外への露出可否。店舗側の読み口は値を載せるだけで、行の絞り込みには使わない（ADR 0015 の負向不変量）。 */
  private boolean published;

  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
