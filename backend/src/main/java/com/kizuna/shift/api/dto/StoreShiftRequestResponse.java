package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店舗側 inbox の出勤希望1件。JSON キーはグローバルの snake_case 設定に従う。
 *
 * <p>work_date / start_time / end_time は申請された内容。変更申請（kind=CHANGE）では current_* に対象シフトの現在値が入り、
 * 店舗は変更前後を並べて判断できる。対象シフトが既に削除されている場合は target_shift_id ごと null になる。
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
  private String status;
  private String kind;
  private String targetShiftId;
  private String decidedBy;
  private OffsetDateTime decidedAt;
  private LocalDate currentWorkDate;
  private LocalTime currentStartTime;
  private LocalTime currentEndTime;
}
