package com.kizuna.shift.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import lombok.Data;

/** 当日実績の記録。予定時刻とは別に保持する。 */
@Data
public class ShiftActualRequest {
  @NotNull private LocalTime startTime;
  @NotNull private LocalTime endTime;
}
