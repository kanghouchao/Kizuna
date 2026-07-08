package com.kizuna.shift.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

@Data
public class ShiftCreateRequest {
  @NotBlank private String castId;
  @NotNull private LocalDate workDate;
  @NotNull private LocalTime startTime;
  @NotNull private LocalTime endTime;
  private String status;
}
