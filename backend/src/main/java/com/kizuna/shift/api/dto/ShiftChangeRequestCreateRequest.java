package com.kizuna.shift.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

/**
 * 確定済みシフトへの変更申請（本人・cast）。
 *
 * <p>店舗は対象シフトから導出するため store_id は受け取らない — 申請者が名乗った店舗を信じると、対象シフトと別店舗の 帰属を主張できてしまう。所属判定も対象シフトの cast_id
 * が本人のものかで行う。
 */
@Data
public class ShiftChangeRequestCreateRequest {
  @NotBlank private String targetShiftId;
  @NotNull private LocalDate workDate;
  @NotNull private LocalTime startTime;
  @NotNull private LocalTime endTime;

  @Size(max = 500, message = "備考は500文字以内で入力してください")
  private String note;
}
