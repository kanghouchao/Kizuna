package com.kizuna.shift.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * 当日実績が 1 回訂正された事実。編集前の行の姿・訂正者・時刻を持つ、法定保存対象の全史である（ADR 0014）。
 *
 * <p>訂正口を持たない項目（キャスト・シフト参照）も写し取る。履歴行だけで編集前の姿が読める形にしておかないと、 現在の行を突き合わせなければ意味が決まらない記録になる。
 *
 * <p>全フィールドが確定した事実で、書き換える操作を持たない。訂正者だけは利用者の削除で欠落しうる（FK が SET NULL）。
 */
@Entity
@Table(name = "t_attendance_corrections")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class AttendanceCorrection extends StoreScopedEntity {

  @Column(name = "attendance_id", nullable = false, updatable = false, length = 64)
  private String attendanceId;

  @Column(name = "cast_id", nullable = false, updatable = false, length = 64)
  private String castId;

  @Column(name = "business_date", nullable = false, updatable = false)
  private LocalDate businessDate;

  @Column(name = "actual_start_at", nullable = false, updatable = false)
  private LocalDateTime actualStartAt;

  @Column(name = "actual_end_at", updatable = false)
  private LocalDateTime actualEndAt;

  @Column(name = "shift_id", updatable = false, length = 64)
  private String shiftId;

  @Column(name = "waiting_place", updatable = false, length = 200)
  private String waitingPlace;

  @Column(name = "corrected_by", updatable = false)
  private Long correctedBy;

  @Column(name = "corrected_at", nullable = false, updatable = false)
  private OffsetDateTime correctedAt;

  private AttendanceCorrection(Attendance before, Long correctedBy, OffsetDateTime correctedAt) {
    this.attendanceId = before.getId();
    this.castId = before.getCastId();
    this.businessDate = before.getBusinessDate();
    this.actualStartAt = before.getActualStartAt();
    this.actualEndAt = before.getActualEndAt();
    this.shiftId = before.getShiftId();
    this.waitingPlace = before.getWaitingPlace();
    this.correctedBy = correctedBy;
    this.correctedAt = correctedAt;
  }

  /** 訂正を適用する<b>前</b>の実績から履歴を起こす。適用後に呼ぶと編集後の姿を編集前として記録してしまう。 */
  public static AttendanceCorrection snapshotOf(
      Attendance before, Long correctedBy, OffsetDateTime correctedAt) {
    return new AttendanceCorrection(before, correctedBy, correctedAt);
  }

  @Override
  public String toString() {
    return "AttendanceCorrection(id="
        + getId()
        + ", attendanceId="
        + attendanceId
        + ", correctedAt="
        + correctedAt
        + ")";
  }
}
