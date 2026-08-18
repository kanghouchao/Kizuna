package com.kizuna.shift.api.dto;

import com.kizuna.shift.domain.ShiftStatus;
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
  private ShiftStatus status;

  /** 店外への露出可否。省略時は公開可で出生する。非公開で生まれる必要がある行だけが false を送る。 */
  private Boolean published;
}
