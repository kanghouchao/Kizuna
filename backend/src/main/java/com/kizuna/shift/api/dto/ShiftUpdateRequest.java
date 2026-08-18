package com.kizuna.shift.api.dto;

import com.kizuna.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

@Data
public class ShiftUpdateRequest {
  private String castId;
  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private ShiftStatus status;
}
