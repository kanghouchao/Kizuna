package com.kizuna.shift.application;

import com.kizuna.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** シフト・出勤希望・当日実績の書き込み命令。実行者は命令に載せず、認証主体から別に受ける。 */
public sealed interface ShiftCommand {

  /** status と published の null は既定値（TENTATIVE・公開可）で出生させる。 */
  record CreateShift(
      String castId,
      LocalDate workDate,
      LocalTime startTime,
      LocalTime endTime,
      ShiftStatus status,
      Boolean published)
      implements ShiftCommand {}

  /** shiftId の null は飛び込み出勤（予定なし）。 */
  record RecordAttendance(
      String castId,
      String shiftId,
      LocalDateTime actualStartAt,
      LocalDateTime actualEndAt,
      String waitingPlace)
      implements ShiftCommand {}

  record CorrectAttendance(
      LocalDate businessDate,
      LocalDateTime actualStartAt,
      LocalDateTime actualEndAt,
      String waitingPlace)
      implements ShiftCommand {}

  record SubmitRequest(
      Long storeId, LocalDate workDate, LocalTime startTime, LocalTime endTime, String note)
      implements ShiftCommand {}

  record SubmitChangeRequest(
      String shiftId, LocalDate workDate, LocalTime startTime, LocalTime endTime, String note)
      implements ShiftCommand {}
}
