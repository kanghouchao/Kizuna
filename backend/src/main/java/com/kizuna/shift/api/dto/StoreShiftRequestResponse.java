package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店舗側 inbox の出勤希望1件。JSON キーはグローバルの snake_case 設定に従う。
 *
 * <p>変更申請（type=CHANGE）は対象シフトの現行日時（current_*）を内联する — 承認判断に「何がどう変わるか」が必要なため。 NEW
 * と、対象シフトを解決しない応答（承認・謝絶の応答）では null。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreShiftRequestResponse {
  private String id;
  private String castId;
  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private String note;
  private String type;
  private String shiftId;
  private String status;
  private LocalDate currentWorkDate;
  private LocalTime currentStartTime;
  private LocalTime currentEndTime;
}
