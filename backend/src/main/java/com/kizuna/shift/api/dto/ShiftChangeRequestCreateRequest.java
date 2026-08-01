package com.kizuna.shift.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/** 確定シフトへの変更申請の提出リクエスト（本人・cast）。対象シフトは shift_id で指定し、店舗はシフトから導出する。 */
@Data
public class ShiftChangeRequestCreateRequest {
  @NotBlank private String shiftId;
  @NotNull private LocalDate workDate;
  @NotNull private LocalTime startTime;
  @NotNull private LocalTime endTime;

  @Size(max = 500, message = "備考は500文字以内で入力してください")
  private String note;
}
