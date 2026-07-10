package com.kizuna.shift.api.dto;

import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 公開出勤表用のシフト情報。shift の id・status・work_date は公開しない。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicShiftResponse {
  private String castId;
  private String castName;
  private String castPhotoUrl;
  private LocalTime startTime;
  private LocalTime endTime;
}
