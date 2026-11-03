package com.kizuna.shift.application;

import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public final class ShiftViews {
  private ShiftViews() {}

  /** 予定を暦日付きで写すための日付変更時刻を、勤務日ごとに解決して添える（設定の読みは application が持つ）。 */
  public record ShiftView(Shift shift, LocalTime dateChangeTime) {}

  /** 店外へ出すシフト。shift の id・status・work_date は持たない。 */
  public record PublicShift(
      String castId,
      String castName,
      String castPhotoUrl,
      LocalTime startTime,
      LocalTime endTime) {}

  /** 導出された欠勤 1 件。行を建てないので識別子を持たない。 */
  public record Absence(String castId, LocalDate businessDate) {}

  /** inbox の 1 件。target は変更申請の対象シフト（NEW と解決できなかった CHANGE では null）。 */
  public record InboxRequest(ShiftRequest request, Shift target, boolean approvable) {}

  /** 行を書いた実行者。利用者の削除で参照が落ちた行では節点ごと null になる。 */
  public record Actor(Long id, String name) {}

  public record ShiftDetail(
      String id,
      String castId,
      LocalDate workDate,
      LocalTime startTime,
      LocalTime endTime,
      String status,
      boolean published,
      Actor createdBy,
      Actor updatedBy,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      RequestLineage origin,
      AttendanceLineage attendance) {}

  public record RequestLineage(
      String id,
      String type,
      String status,
      String castId,
      LocalDate workDate,
      LocalTime startTime,
      LocalTime endTime,
      String note,
      LocalDate originalWorkDate,
      LocalTime originalStartTime,
      LocalTime originalEndTime,
      OffsetDateTime createdAt,
      Actor processedBy,
      OffsetDateTime processedAt) {}

  public record AttendanceLineage(
      String id,
      String castId,
      LocalDate businessDate,
      LocalDateTime actualStartAt,
      LocalDateTime actualEndAt,
      String waitingPlace,
      Actor createdBy,
      Actor updatedBy,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}
}
