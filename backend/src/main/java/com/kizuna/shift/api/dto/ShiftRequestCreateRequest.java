package com.kizuna.shift.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

@Data
public class ShiftRequestCreateRequest {
  @NotNull private Long storeId;
  @NotNull private LocalDate workDate;
  @NotNull private LocalTime startTime;
  @NotNull private LocalTime endTime;

  @Size(max = 500, message = "備考は500文字以内で入力してください")
  private String note;
}
